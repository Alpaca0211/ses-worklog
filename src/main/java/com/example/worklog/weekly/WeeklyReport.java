package com.example.worklog.weekly;

import java.time.LocalDate;
import java.util.List;

/**
 * 週報 1 週分の生成結果。
 *
 * <p>いずれも下書きであり、週報システムへの書き込みは行わない。
 * 利用者が内容を確認したうえで貼り付ける。
 */
public record WeeklyReport(
        ReportWeek week,
        List<DailyContent> days,
        WeeklySummary summary,
        String performanceFirst,
        String performanceSecond) {

    /** 1 日分の作業内容欄テキスト。 */
    public record DailyContent(LocalDate date, String dayOfWeek, String content) {

        public boolean isEmpty() {
            return content == null || content.isBlank();
        }
    }

    /** 【業務遂行】欄に貼り付ける全文。2 文目が無ければ 1 文目だけ。 */
    public String performanceText() {
        String first = performanceFirst == null ? "" : performanceFirst.trim();
        String second = performanceSecond == null ? "" : performanceSecond.trim();
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return first + "\n" + second;
    }

    public boolean hasSecondSentence() {
        return performanceSecond != null && !performanceSecond.isBlank();
    }

    /** 記録のある日だけを返す。週報システムには空の日を貼る必要が無いため。 */
    public List<DailyContent> filledDays() {
        return days.stream().filter(d -> !d.isEmpty()).toList();
    }
}
