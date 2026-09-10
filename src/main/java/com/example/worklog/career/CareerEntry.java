package com.example.worklog.career;

import java.time.LocalDate;
import java.util.List;

/**
 * 職務経歴書に貼り付ける 1 案件分の記述。
 *
 * <p>社外に出るため、案件名・システム名はすべて伏せる。
 * 案件の識別には、案件ごとに設定した社外向けの説明を使う。
 */
public record CareerEntry(
        String publicLabel,
        LocalDate start,
        LocalDate end,
        int months,
        int totalItems,
        List<TaskCount> taskCounts) {

    public record TaskCount(String name, long count) {
    }

    /** 期間表記。「2026年4月 〜 2026年8月（5ヶ月）」 */
    public String period() {
        return "%d年%d月 〜 %d年%d月（%dヶ月）"
                .formatted(start.getYear(), start.getMonthValue(),
                        end.getYear(), end.getMonthValue(), months);
    }

    /** 担当業務の一行表記。「リリース対応 42件、レビュー依頼 20件」 */
    public String taskSummary() {
        return taskCounts.stream()
                .map(t -> t.name() + " " + t.count() + "件")
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
    }
}
