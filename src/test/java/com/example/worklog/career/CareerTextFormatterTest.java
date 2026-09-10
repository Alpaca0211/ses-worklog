package com.example.worklog.career;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 職務経歴テキストの組み立て。決定論的処理なので出力を完全に固定できる。 */
class CareerTextFormatterTest {

    private final CareerTextFormatter formatter = new CareerTextFormatter();

    private CareerEntry entry(String label) {
        return new CareerEntry(label,
                LocalDate.of(2026, 4, 6), LocalDate.of(2026, 8, 28), 5, 62,
                List.of(new CareerEntry.TaskCount("リリース対応", 42),
                        new CareerEntry.TaskCount("レビュー依頼", 20)));
    }

    @Test
    void 案件ごとに期間と担当業務と実績を組み立てる() {
        String text = formatter.format(new CareerDraft(
                List.of(entry("Webサービスの保守開発")),
                List.of("手順の属人化を解消するため、動作確認手順を文書化しチームへ展開した。")));

        // 取り組みは案件別ではないため、空行を挟んで独立した節にする
        assertThat(text).isEqualTo("""
                【担当案件】Webサービスの保守開発
                【期間】2026年4月 〜 2026年8月（5ヶ月）
                【担当業務】リリース対応 42件、レビュー依頼 20件

                【取り組み・実績】
                ・手順の属人化を解消するため、動作確認手順を文書化しチームへ展開した。""");
    }

    @Test
    void 実績が無ければその見出しごと省く() {
        String text = formatter.format(
                new CareerDraft(List.of(entry("Webサービスの保守開発")), List.of()));

        assertThat(text).doesNotContain("取り組み・実績");
        assertThat(text).endsWith("【担当業務】リリース対応 42件、レビュー依頼 20件");
    }

    @Test
    void 複数案件は空行で区切る() {
        String text = formatter.format(new CareerDraft(
                List.of(entry("Webサービスの保守開発"), entry("業務システムの改修")), List.of()));

        assertThat(text).contains("Webサービスの保守開発", "業務システムの改修");
        assertThat(text.split("【担当案件】")).hasSize(3);
    }

    @Test
    void 対象が無ければ空文字を返す() {
        assertThat(formatter.format(new CareerDraft(List.of(), List.of()))).isEmpty();
        assertThat(formatter.format(null)).isEmpty();
    }

    @Test
    void 取り組みは案件別ではなく期間全体として末尾に置く() {
        // 日次メモは案件に紐付かないため、特定の案件の実績として示すと事実に反する
        String text = formatter.format(new CareerDraft(
                List.of(entry("Webサービスの保守開発"), entry("業務システムの改修")),
                List.of("動作確認手順を文書化しチームへ展開した。")));

        assertThat(text.indexOf("【取り組み・実績】")).isGreaterThan(text.indexOf("業務システムの改修"));
        assertThat(text).endsWith("・動作確認手順を文書化しチームへ展開した。");
    }
}
