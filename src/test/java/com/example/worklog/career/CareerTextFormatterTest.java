package com.example.worklog.career;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.domain.TechnologyCategory;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** スキルシートのテキスト組み立て。決定論的処理なので出力を完全に固定できる。 */
class CareerTextFormatterTest {

    private final CareerTextFormatter formatter = new CareerTextFormatter();

    private CareerEntry entry(String label) {
        return new CareerEntry(
                "金融",
                label,
                "大手◯◯で用いる融資システムの保守開発。",
                "PL(1名)、メンバー(自身含む4名)",
                "プロジェクト画面数:約300、担当画面数:5",
                "Windows",
                LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 8, 28),
                5,
                62,
                List.of(new CareerEntry.TaskCount("リリース対応", 42),
                        new CareerEntry.TaskCount("レビュー依頼", 20)),
                List.of("Java", "Spring Boot"));
    }

    private CareerEntry minimal(String label) {
        return new CareerEntry(null, label, null, null, null, null,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), 1, 0, List.of(), List.of());
    }

    @Test
    void 業務実績をシートの項目順に組み立てる() {
        String text = formatter.format(
                new CareerDraft(List.of(entry("融資管理システム金利計算対応")), List.of(), List.of()));

        assertThat(text).isEqualTo("""
                ■ 業務実績
                1. 金融
                ≪融資管理システム金利計算対応≫
                [概要]
                大手◯◯で用いる融資システムの保守開発。
                [担当業務]
                リリース対応 42件、レビュー依頼 20件
                [チーム構成]
                PL(1名)、メンバー(自身含む4名)
                [規模]
                プロジェクト画面数:約300、担当画面数:5
                期間: 2026年4月 〜 2026年8月
                稼動月数: 0年5ヶ月
                環境: Windows
                使用技術: Java / Spring Boot""");
    }

    @Test
    void 未入力の項目は見出しごと省く() {
        String text = formatter.format(new CareerDraft(List.of(minimal("担当案件")), List.of(), List.of()));

        assertThat(text).doesNotContain("[概要]", "[チーム構成]", "[規模]", "環境:", "使用技術:");
        assertThat(text).contains("≪担当案件≫", "期間: 2026年4月 〜 2026年4月", "稼動月数: 0年1ヶ月");
    }

    @Test
    void 技能歴は区分ごとに経験年数付きで並べる() {
        String text = formatter.format(new CareerDraft(List.of(), List.of(), List.of(
                new TechnologyExperience("Java", TechnologyCategory.LANGUAGE_FRAMEWORK, 38),
                new TechnologyExperience("Git", TechnologyCategory.OS_TOOL, 12))));

        assertThat(text).isEqualTo("""
                ■ 技能歴
                【言語・フレームワーク】
                Java\t3年2ヶ月
                【OS・その他（ツールなど）】
                Git\t1年0ヶ月""");
    }

    @Test
    void 取り組みは案件別ではなく末尾にまとめる() {
        String text = formatter.format(new CareerDraft(
                List.of(minimal("案件1"), minimal("案件2")),
                List.of("動作確認手順を文書化しチームへ展開した。"),
                List.of()));

        assertThat(text.indexOf("■ 取り組み・実績")).isGreaterThan(text.indexOf("≪案件2≫"));
        assertThat(text).endsWith("・動作確認手順を文書化しチームへ展開した。");
    }

    @Test
    void 対象が無ければ空文字を返す() {
        assertThat(formatter.format(new CareerDraft(List.of(), List.of(), List.of()))).isEmpty();
        assertThat(formatter.format(null)).isEmpty();
    }
}
