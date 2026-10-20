package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.career.EvaluationService;
import com.example.worklog.domain.*;
import com.example.worklog.masking.TermService;
import com.example.worklog.pipeline.DailyLogService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 評価期間ごとの材料集計。
 *
 * <p>狙いは文章を作ることではなく、材料が無い項目が見えること。
 * 期末に探しても評価は作れないため、期中に不足へ気付けるかどうかを検証する。
 */
class EvaluationMaterialsTest extends SanitizeTestBase {

    @Autowired
    EvaluationService service;

    @Autowired
    DailyLogService logService;

    @Autowired
    DailyLogRepository logs;

    @Autowired
    EvaluationItemRepository items;

    @Autowired
    TermService termService;

    private EvaluationItem performance;
    private EvaluationItem communication;
    private EvaluationItem learning;

    @BeforeEach
    void setUp() {
        logs.deleteAll();
        items.deleteAll();
        performance = items.save(new EvaluationItem("業務遂行", 1));
        communication = items.save(new EvaluationItem("報連相", 2));
        learning = items.save(new EvaluationItem("自己啓発・学習", 3));
    }

    private DailyLog record(String date, String text, EvaluationItem... tagged) {
        return logService.record(LocalDate.parse(date), text,
                List.of(tagged).stream().map(EvaluationItem::getId).toList());
    }

    private EvaluationService.EvaluationSummary summarize() {
        return service.summarize(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
    }

    @Test
    void タグを付けたメモが項目別に集まる() {
        record("2026-10-05", "リリース手順の合意を取った", performance);
        record("2026-10-06", "懸念を早めに共有した", communication);
        record("2026-10-07", "別件の調整も行った", performance, communication);

        var summary = summarize();

        assertThat(summary.items()).extracting(
                        EvaluationService.ItemMaterials::itemName, EvaluationService.ItemMaterials::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("業務遂行", 2),
                        org.assertj.core.groups.Tuple.tuple("報連相", 2),
                        org.assertj.core.groups.Tuple.tuple("自己啓発・学習", 0));
    }

    @Test
    void 材料が無い項目が分かる() {
        record("2026-10-05", "リリース手順の合意を取った", performance);

        assertThat(summarize().emptyItems()).containsExactly("報連相", "自己啓発・学習");
        assertThat(summarize().hasEmptyItems()).isTrue();
    }

    @Test
    void すべての項目に材料があれば警告は出ない() {
        record("2026-10-05", "あ", performance);
        record("2026-10-06", "い", communication);
        record("2026-10-07", "う", learning);

        assertThat(summarize().hasEmptyItems()).isFalse();
    }

    @Test
    void 期間外のメモは含めない() {
        record("2026-09-30", "期間の前", performance);
        record("2026-11-01", "期間の後", performance);

        assertThat(summarize().items().get(0).count()).isZero();
    }

    @Test
    void 分類していないメモは件数で分かる() {
        record("2026-10-05", "分類済み", performance);
        record("2026-10-06", "分類していない");

        assertThat(summarize().untaggedCount()).isEqualTo(1);
        assertThat(service.untagged(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")))
                .hasSize(1);
    }

    @Test
    void 材料は社内向けマスキングを通る() {
        // 評価は自社へ出すものなので案件名は残し、人名は伏せる
        termService.add("オリオン物流", "顧客企業", TermCategory.CLIENT);
        termService.add("小田切", "チームメンバー", TermCategory.PERSON);
        record("2026-10-05", "オリオン物流の件で小田切と調整した", performance);

        String text = summarize().items().get(0).asText();

        assertThat(text).contains("オリオン物流").doesNotContain("小田切");
    }

    @Test
    void 後からタグを付け直せる() {
        DailyLog saved = record("2026-10-05", "分類していない");
        assertThat(summarize().untaggedCount()).isEqualTo(1);

        logService.tag(saved.getId(), List.of(performance.getId()));

        assertThat(summarize().untaggedCount()).isZero();
        assertThat(summarize().items().get(0).count()).isEqualTo(1);
    }
}
