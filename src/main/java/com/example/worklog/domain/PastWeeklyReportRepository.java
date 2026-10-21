package com.example.worklog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PastWeeklyReportRepository extends JpaRepository<PastWeeklyReport, Long> {

    /**
     * 期間内に提出した週報の件数。評価項目「週報」は提出の量と内容が対象なので、
     * 日次メモではなく週報そのものを数える。
     */
    @org.springframework.data.jpa.repository.Query(
            "select count(r) from PastWeeklyReport r"
                    + " where r.reportYear * 100 + r.reportMonth between :fromYm and :toYm")
    long countInMonthRange(@org.springframework.data.repository.query.Param("fromYm") int fromYm,
                           @org.springframework.data.repository.query.Param("toYm") int toYm);

    Optional<PastWeeklyReport> findByReportYearAndReportMonthAndWeekNum(int reportYear, int reportMonth, int weekNum);

    List<PastWeeklyReport> findAllByOrderByReportYearDescReportMonthDescWeekNumDesc();
}
