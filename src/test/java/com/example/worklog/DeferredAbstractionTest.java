package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.worklog.abstraction.AbstractionService;
import com.example.worklog.abstraction.LlmStatus;
import com.example.worklog.domain.DailyLog;
import com.example.worklog.domain.DailyLogRepository;
import com.example.worklog.domain.SanitizeStatus;
import com.example.worklog.domain.TermCategory;
import com.example.worklog.masking.TermService;
import com.example.worklog.pipeline.AbstractionBacklog;
import com.example.worklog.pipeline.DailyLogService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 抽象化を保存から切り離したこと。
 *
 * <p>狙いは記録が続くこと。些細な記録ほど件数が多いため、1 件ごとに数秒の LLM 往復を
 * 待たせると記録そのものが止まる。保存は決定論的な辞書マスキングだけで確定し、
 * 文章の整形はあとから追いかける。
 */
class DeferredAbstractionTest extends SanitizeTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

    @Autowired
    DailyLogService logService;

    @Autowired
    AbstractionBacklog backlog;

    @Autowired
    DailyLogRepository logs;

    @Autowired
    TermService termService;

    @MockitoBean
    AbstractionService abstractionService;

    @BeforeEach
    void setUp() {
        logs.deleteAll();
        termService.add("ヴェガ銀行", "顧客企業", TermCategory.CLIENT);
        when(abstractionService.status()).thenReturn(LlmStatus.READY);
    }

    private DailyLog reload(Long id) {
        return logs.findById(id).orElseThrow();
    }

    @Test
    void 保存は辞書マスキングだけで確定する() {
        // LLM が応答する設定でも、保存の戻り値は抽象化を待っていない
        when(abstractionService.abstractText(anyString())).thenReturn(Optional.of("整形後の文章"));

        DailyLog saved = logService.record(DAY, "ヴェガ銀行の定例に参加", null);

        assertThat(saved.getStatus()).isEqualTo(SanitizeStatus.MASKED_ONLY);
        assertThat(saved.getOutputText()).isEqualTo(saved.getMaskedText());
        assertThat(saved.getOutputText()).doesNotContain("ヴェガ銀行").contains("顧客企業");
    }

    @Test
    void 後から抽象化すると出力だけが差し替わる() {
        when(abstractionService.abstractText(anyString())).thenReturn(Optional.of("顧客企業の定例会に参加した"));
        DailyLog saved = logService.record(DAY, "ヴェガ銀行の定例に参加", null);
        String maskedAtSave = saved.getMaskedText();

        assertThat(backlog.abstractOne(saved.getId())).isTrue();

        DailyLog after = reload(saved.getId());
        assertThat(after.getStatus()).isEqualTo(SanitizeStatus.ABSTRACTED);
        assertThat(after.getOutputText()).isEqualTo("顧客企業の定例会に参加した");
        // 一次情報は書き換えない。辞書を直したあとに再処理できる必要があるため
        assertThat(after.getRawText()).isEqualTo("ヴェガ銀行の定例に参加");
        assertThat(after.getMaskedText()).isEqualTo(maskedAtSave);
    }

    @Test
    void LLMが止まっていた期間の記録をあとから拾える() {
        when(abstractionService.abstractText(anyString())).thenReturn(Optional.empty());
        DailyLog a = logService.record(DAY, "手順書を直した", null);
        DailyLog b = logService.record(DAY, "後輩に聞かれて教えた", null);

        assertThat(backlog.pendingCount()).isEqualTo(2);
        assertThat(backlog.abstractOne(a.getId())).isFalse();
        assertThat(reload(a.getId()).getStatus()).isEqualTo(SanitizeStatus.MASKED_ONLY);

        // LLM が復帰したらまとめて拾い直せる
        when(abstractionService.abstractText(anyString())).thenReturn(Optional.of("整形後の文章"));

        assertThat(backlog.abstractPending(50)).isEqualTo(2);
        assertThat(backlog.pendingCount()).isZero();
        assertThat(reload(b.getId()).getOutputText()).isEqualTo("整形後の文章");
    }

    @Test
    void 後追いの抽象化にも出力ゲートがかかる() {
        // LLM 出力に禁止用語が再出現しても、保存される出力には残らない
        when(abstractionService.abstractText(anyString())).thenReturn(Optional.of("ヴェガ銀行の定例に参加した"));
        DailyLog saved = logService.record(DAY, "定例に参加", null);

        assertThat(backlog.abstractOne(saved.getId())).isTrue();

        DailyLog after = reload(saved.getId());
        assertThat(after.getStatus()).isEqualTo(SanitizeStatus.ABSTRACTED_REMASKED);
        assertThat(after.getOutputText()).doesNotContain("ヴェガ銀行").contains("顧客企業");
    }

    @Test
    void 抽象化済みの記録は二重に処理しない() {
        when(abstractionService.abstractText(anyString())).thenReturn(Optional.of("一度目の整形"));
        DailyLog saved = logService.record(DAY, "定例に参加", null);
        assertThat(backlog.abstractOne(saved.getId())).isTrue();

        when(abstractionService.abstractText(anyString())).thenReturn(Optional.of("二度目の整形"));

        assertThat(backlog.abstractOne(saved.getId())).isFalse();
        assertThat(reload(saved.getId()).getOutputText()).isEqualTo("一度目の整形");
    }

    @Test
    void 抽象化が例外で落ちても記録は残る() {
        when(abstractionService.abstractText(anyString())).thenThrow(new IllegalStateException("LLM 異常"));
        DailyLog saved = logService.record(DAY, "ヴェガ銀行の定例に参加", null);

        assertThat(backlog.abstractOne(saved.getId())).isFalse();

        DailyLog after = reload(saved.getId());
        assertThat(after.getStatus()).isEqualTo(SanitizeStatus.MASKED_ONLY);
        assertThat(after.getRawText()).isEqualTo("ヴェガ銀行の定例に参加");
    }

    @Test
    void 保存後に自動で抽象化が走る() throws Exception {
        when(abstractionService.abstractText(anyString())).thenReturn(Optional.of("自動で整形された文章"));

        DailyLog saved = logService.record(DAY, "定例に参加", null);

        // コミット後に別スレッドで走るため、完了を待つ
        SanitizeStatus status = SanitizeStatus.MASKED_ONLY;
        for (int i = 0; i < 100 && status == SanitizeStatus.MASKED_ONLY; i++) {
            Thread.sleep(50);
            status = reload(saved.getId()).getStatus();
        }

        assertThat(status).isEqualTo(SanitizeStatus.ABSTRACTED);
        assertThat(reload(saved.getId()).getOutputText()).isEqualTo("自動で整形された文章");
    }
}
