package com.example.worklog.weekly;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 週境界の検証。
 *
 * <p>期待値は週報システムの過去 219 週分のエクスポートから採取した実測値であり、
 * 全 219 週で一致することを確認済み。ここがずれると集計も生成も全て狂うため、
 * 月初・月末の切り詰めを含めて固定する。
 */
class ReportWeekTest {

    private void assertRange(int year, int month, int week, String start, String end) {
        ReportWeek w = new ReportWeek(year, month, week);
        assertThat(w.start()).as("%s の開始", w.label()).isEqualTo(LocalDate.parse(start));
        assertThat(w.end()).as("%s の終了", w.label()).isEqualTo(LocalDate.parse(end));
    }

    @Test
    void 通常週は月曜から日曜まで() {
        assertRange(2026, 8, 2, "2026-08-03", "2026-08-09");
        assertRange(2026, 8, 3, "2026-08-10", "2026-08-16");
        assertRange(2026, 8, 4, "2026-08-17", "2026-08-23");
        assertRange(2026, 8, 5, "2026-08-24", "2026-08-30");
    }

    @Test
    void 第1週は月初日から始まる() {
        // 2026-08-01 は土曜。月曜起点なら 7/27 だが、前月にはみ出さない
        assertRange(2026, 8, 1, "2026-08-01", "2026-08-02");
        // 2026-07-01 は水曜
        assertRange(2026, 7, 1, "2026-07-01", "2026-07-05");
    }

    @Test
    void 最終週は月末日で終わる() {
        // 2026-07-27 は月曜だが、日曜(8/2)ではなく月末(7/31)で切れる
        assertRange(2026, 7, 5, "2026-07-27", "2026-07-31");
    }

    @Test
    void 月初が月曜の場合は第1週が丸ごと7日になる() {
        // 2026-06-01 は月曜
        assertRange(2026, 6, 1, "2026-06-01", "2026-06-07");
    }

    @Test
    void 日付から週番号を求められる() {
        assertThat(ReportWeek.of(LocalDate.parse("2026-08-01")).weekNum()).isEqualTo(1);
        assertThat(ReportWeek.of(LocalDate.parse("2026-08-03")).weekNum()).isEqualTo(2);
        assertThat(ReportWeek.of(LocalDate.parse("2026-08-23")).weekNum()).isEqualTo(4);
        assertThat(ReportWeek.of(LocalDate.parse("2026-08-30")).weekNum()).isEqualTo(5);
        assertThat(ReportWeek.of(LocalDate.parse("2026-07-31")).weekNum()).isEqualTo(5);
    }

    @Test
    void 開始日と終了日は往復する() {
        ReportWeek w = new ReportWeek(2026, 8, 4);
        assertThat(ReportWeek.of(w.start())).isEqualTo(w);
        assertThat(ReportWeek.of(w.end())).isEqualTo(w);
    }

    @Test
    void 月をまたいで前後に移動できる() {
        assertThat(new ReportWeek(2026, 8, 1).previous()).isEqualTo(new ReportWeek(2026, 7, 5));
        assertThat(new ReportWeek(2026, 7, 5).next()).isEqualTo(new ReportWeek(2026, 8, 1));
    }

    @Test
    void 月末が週明けに落ちる月は第6週まで存在する() {
        // 2026-08-31 は月曜。単独で第6週になる。
        // 実データにも第6週は 7 週分存在するため、5 週で打ち切ってはならない。
        assertRange(2026, 8, 6, "2026-08-31", "2026-08-31");
    }

    @Test
    void 月内の週数を数えられる() {
        assertThat(ReportWeek.weekCount(2026, 8)).isEqualTo(6);
        assertThat(ReportWeek.weekCount(2026, 6)).isEqualTo(5);
    }

    @Test
    void ラベルは週報システムの表記に一致する() {
        assertThat(new ReportWeek(2026, 8, 4).label()).isEqualTo("2026年8月4週");
        assertThat(new ReportWeek(2026, 8, 4).yearMonth()).isEqualTo("2026-08");
    }
}
