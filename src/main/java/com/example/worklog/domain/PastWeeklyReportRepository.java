package com.example.worklog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PastWeeklyReportRepository extends JpaRepository<PastWeeklyReport, Long> {

    Optional<PastWeeklyReport> findByReportYearAndReportMonthAndWeekNum(int reportYear, int reportMonth, int weekNum);

    List<PastWeeklyReport> findAllByOrderByReportYearDescReportMonthDescWeekNumDesc();
}
