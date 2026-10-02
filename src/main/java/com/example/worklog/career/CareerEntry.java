package com.example.worklog.career;

import java.time.LocalDate;
import java.util.List;

/**
 * スキルシートの業務実績 1 ブロック分。
 *
 * <p>社外に出るため、案件名・システム名はすべて伏せる。
 * 案件の識別には、案件ごとに設定した社外向けの名称を使う。
 */
public record CareerEntry(
        String industry,
        String publicLabel,
        String overview,
        String teamComposition,
        String projectScale,
        String environment,
        LocalDate start,
        LocalDate end,
        int months,
        int totalItems,
        List<TaskCount> taskCounts,
        List<String> technologies) {

    public record TaskCount(String name, long count) {
    }

    /** 期間表記。「2026年4月 〜 2026年9月」 */
    public String periodRange() {
        return "%d年%d月 〜 %d年%d月".formatted(
                start.getYear(), start.getMonthValue(), end.getYear(), end.getMonthValue());
    }

    /** 稼動月数。シートの表記に合わせて「0年7ヶ月」の形にする。 */
    public String duration() {
        return Durations.format(months);
    }

    /** 期間と稼動月数をまとめた表記。 */
    public String period() {
        return periodRange() + "（" + duration() + "）";
    }

    /** 担当業務の一行表記。「リリース対応 42件、レビュー依頼 20件」 */
    public String taskSummary() {
        return taskCounts.stream()
                .map(t -> t.name() + " " + t.count() + "件")
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
    }

    public String technologySummary() {
        return technologies.stream().reduce((a, b) -> a + " / " + b).orElse("");
    }

    public boolean has(String value) {
        return value != null && !value.isBlank();
    }
}
