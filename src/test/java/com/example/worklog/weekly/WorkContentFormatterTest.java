package com.example.worklog.weekly;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.domain.Project;
import com.example.worklog.domain.TaskType;
import com.example.worklog.domain.WorkEntry;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 作業内容欄のテキスト組み立て。LLM を使わない決定論的処理なので出力を完全に固定できる。 */
class WorkContentFormatterTest {

    private final WorkContentFormatter formatter =
            new WorkContentFormatter(new WorkFormatProperties(null, null, null));

    private final LocalDate date = LocalDate.of(2026, 8, 24);
    private final Project projectA = new Project("案件A", 1);
    private final Project projectB = new Project("案件B", 2);
    private final TaskType release = new TaskType("リリース対応", 1);
    private final TaskType review = new TaskType("レビュー依頼", 2);

    @Test
    void 案件ごとにグルーピングして組み立てる() {
        String text = formatter.formatDay(List.of(
                new WorkEntry(date, projectA, "脆弱性対応（8月）", List.of(release), "service_a：stg、service_b：stg"),
                new WorkEntry(date, projectA, "脆弱性対応（8月）", List.of(review), "service_c"),
                new WorkEntry(date, projectB, "月次点検", List.of(release), "service_d：prod")));

        assertThat(text).isEqualTo("""
                ■やったこと
                ▶ 案件A
                ・脆弱性対応（8月） リリース対応（service_a：stg、service_b：stg）
                ・脆弱性対応（8月） レビュー依頼（service_c）

                ▶ 案件B
                ・月次点検 リリース対応（service_d：prod）""");
    }

    @Test
    void 施策名が空なら作業種別だけを書く() {
        String text = formatter.formatDay(List.of(
                new WorkEntry(date, projectA, "", List.of(review), "service_c")));

        assertThat(text).contains("・レビュー依頼（service_c）");
        assertThat(text).doesNotContain("  ");
    }

    @Test
    void 対象が空なら括弧を付けない() {
        String text = formatter.formatDay(List.of(
                new WorkEntry(date, projectA, "調査", List.of(review), null)));

        assertThat(text).endsWith("・調査 レビュー依頼");
        assertThat(text).doesNotContain("（）");
    }

    @Test
    void 複数の作業種別はスラッシュで連結する() {
        // 同一対象に対して複数のアクションを行う日がある。
        // 別々の行にすると対象の記載が重複するため、実運用では連結して1行にしている。
        TaskType create = new TaskType("PR作成", 5);
        String text = formatter.formatDay(List.of(
                new WorkEntry(date, projectA, "脆弱性対応（8月）", List.of(create, review), "service_a")));

        assertThat(text).contains("・脆弱性対応（8月） PR作成/レビュー依頼（service_a）");
    }

    @Test
    void 選択した順序が連結順になる() {
        TaskType create = new TaskType("PR作成", 5);
        String forward = formatter.formatDay(List.of(
                new WorkEntry(date, projectA, null, List.of(create, review), "x")));
        String reverse = formatter.formatDay(List.of(
                new WorkEntry(date, projectA, null, List.of(review, create), "x")));

        assertThat(forward).contains("PR作成/レビュー依頼");
        assertThat(reverse).contains("レビュー依頼/PR作成");
    }

    @Test
    void 記録が無い日は空文字を返す() {
        assertThat(formatter.formatDay(List.of())).isEmpty();
        assertThat(formatter.formatDay(null)).isEmpty();
    }

    @Test
    void 書式は設定で差し替えられる() {
        // 作業内容欄の記法は週報システムの仕様ではなく利用者独自のものなので、固定しない
        WorkContentFormatter custom =
                new WorkContentFormatter(new WorkFormatProperties("## やったこと", "### ", "- "));

        String text = custom.formatDay(List.of(
                new WorkEntry(date, projectA, null, List.of(review), "service_c")));

        assertThat(text).isEqualTo("""
                ## やったこと
                ### 案件A
                - レビュー依頼（service_c）""");
    }
}
