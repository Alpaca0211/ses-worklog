package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.worklog.career.EvaluationService;
import com.example.worklog.domain.DailyLog;
import com.example.worklog.domain.DailyLogRepository;
import com.example.worklog.domain.SanitizeStatus;
import com.example.worklog.domain.TermCategory;
import com.example.worklog.masking.TermService;
import com.example.worklog.pipeline.DailyLogService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 記録の書き直しと削除、および短文を抽象化しないこと。
 *
 * <p>些細な記録を数多く残す使い方では、打ち間違いを直せないことと、
 * 短い入力に LLM が書かれていない内容を補うことが、どちらも記録の信頼性を壊す。
 */
class DailyLogEditTest extends SanitizeTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

    @Autowired
    DailyLogService logService;

    @Autowired
    DailyLogRepository logs;

    @Autowired
    EvaluationService evaluationService;

    @Autowired
    TermService termService;

    @BeforeEach
    void setUp() {
        logs.deleteAll();
        termService.add("ヴェガ銀行", "顧客企業", TermCategory.CLIENT);
    }

    private DailyLog reload(Long id) {
        return logs.findById(id).orElseThrow();
    }

    @Test
    void 短い記録は抽象化を通さない() {
        DailyLog saved = logService.record(DAY, "自社の帰社日に出席した。", null);

        assertThat(saved.getStatus()).isEqualTo(SanitizeStatus.ABSTRACTION_SKIPPED);
        // 書いたとおりが残る。補完された文章に置き換わらない
        assertThat(saved.getOutputText()).isEqualTo("自社の帰社日に出席した。");
    }

    @Test
    void 長い記録は抽象化の対象になる() {
        String longText = "ヴェガ銀行の勘定系で発生した障害について原因を調査し、"
                + "コネクションプールの枯渇であることを特定したうえでチームへ共有した。";

        DailyLog saved = logService.record(DAY, longText, null);

        // LLM 無効のテスト環境では縮退するが、抽象化の対象としては扱われる
        assertThat(saved.getStatus()).isEqualTo(SanitizeStatus.MASKED_ONLY);
    }

    @Test
    void 書き直すと秘匿化をやり直す() {
        DailyLog saved = logService.record(DAY, "定例に参加した。", null);

        logService.update(saved.getId(), "ヴェガ銀行の定例に参加した。");

        DailyLog after = reload(saved.getId());
        assertThat(after.getRawText()).isEqualTo("ヴェガ銀行の定例に参加した。");
        assertThat(after.getOutputText()).doesNotContain("ヴェガ銀行").contains("顧客企業");
        assertThat(after.getMaskedCount()).isEqualTo(1);
    }

    @Test
    void 書き直しても記録は増えない() {
        DailyLog saved = logService.record(DAY, "定例に参加した。", null);

        logService.update(saved.getId(), "定例に参加し、議事録を共有した。");

        assertThat(logs.count()).isEqualTo(1);
        assertThat(reload(saved.getId()).getRawText()).isEqualTo("定例に参加し、議事録を共有した。");
    }

    @Test
    void 削除すると評価項目の紐づけごと消える() {
        var item = evaluationService.items().get(0);
        DailyLog saved = logService.record(DAY, "定例に参加した。", List.of(item.getId()));
        assertThat(logs.count()).isEqualTo(1);

        logService.delete(saved.getId());

        assertThat(logs.count()).isZero();
        assertThatThrownBy(() -> logService.find(saved.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
