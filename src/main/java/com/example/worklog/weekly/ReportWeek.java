package com.example.worklog.weekly;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * 週報システムの「年月週」（例: 2026年8月4週）。
 *
 * <p>規則は過去 219 週分の実データから確定させたもので、次の 2 点が要点:
 * <ul>
 *   <li>週は月曜起点・日曜終わり</li>
 *   <li>週は月をまたがない。第 1 週は月初日から始まり、最終週は月末日で終わる</li>
 * </ul>
 * ISO 週番号とも「月内の第 n 月曜」とも異なるため、独自に実装する。
 */
public record ReportWeek(int year, int month, int weekNum) {

    public static ReportWeek of(LocalDate date) {
        LocalDate mondayOfWeek1 = mondayOfWeek1(date.getYear(), date.getMonthValue());
        LocalDate mondayOfDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int n = (int) ChronoUnit.WEEKS.between(mondayOfWeek1, mondayOfDate) + 1;
        return new ReportWeek(date.getYear(), date.getMonthValue(), n);
    }

    /** その月の 1 日を含む週の月曜日。前月にはみ出しうる。 */
    private static LocalDate mondayOfWeek1(int year, int month) {
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate start() {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate monday = mondayOfWeek1(year, month).plusWeeks(weekNum - 1L);
        // 第 1 週は月初日で切り詰める（前月へはみ出さない）
        return monday.isBefore(firstDay) ? firstDay : monday;
    }

    public LocalDate end() {
        LocalDate sunday = start().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate lastDay = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
        // 最終週は月末日で切り詰める（翌月へはみ出さない）
        return sunday.isAfter(lastDay) ? lastDay : sunday;
    }

    /** その月に存在する週数。 */
    public static int weekCount(int year, int month) {
        LocalDate lastDay = LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
        return of(lastDay).weekNum();
    }

    public ReportWeek previous() {
        if (weekNum > 1) {
            return new ReportWeek(year, month, weekNum - 1);
        }
        LocalDate prevMonth = LocalDate.of(year, month, 1).minusMonths(1);
        return new ReportWeek(prevMonth.getYear(), prevMonth.getMonthValue(),
                weekCount(prevMonth.getYear(), prevMonth.getMonthValue()));
    }

    public ReportWeek next() {
        if (weekNum < weekCount(year, month)) {
            return new ReportWeek(year, month, weekNum + 1);
        }
        LocalDate nextMonth = LocalDate.of(year, month, 1).plusMonths(1);
        return new ReportWeek(nextMonth.getYear(), nextMonth.getMonthValue(), 1);
    }

    /** 週報システムの表記に合わせたラベル。 */
    public String label() {
        return year + "年" + month + "月" + weekNum + "週";
    }

    /** 月選択欄の値（例: 2026-08）。 */
    public String yearMonth() {
        return String.format("%04d-%02d", year, month);
    }
}
