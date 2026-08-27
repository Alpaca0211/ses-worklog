package com.example.worklog.weekly;

import java.util.List;

/**
 * 週次の実績集計。
 *
 * <p>構造化された作業記録を数えるだけなので、捏造の余地が原理的に無い。
 * 【業務遂行】の「滞りなく」という主観表現を、事実の数字で裏付けるために使う。
 */
public record WeeklySummary(
        ReportWeek week,
        int totalItems,
        int activeDays,
        List<CountItem> byTaskType,
        List<CountItem> byProject) {

    public record CountItem(String name, long count) {
    }

    public boolean isEmpty() {
        return totalItems == 0;
    }
}
