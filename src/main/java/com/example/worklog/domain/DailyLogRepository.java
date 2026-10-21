package com.example.worklog.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyLogRepository extends JpaRepository<DailyLog, Long> {

    List<DailyLog> findAllByOrderByWorkDateDescIdDesc(Pageable pageable);

    List<DailyLog> findByWorkDateBetweenOrderByWorkDateAsc(LocalDate from, LocalDate to);

    /** 抽象化がまだ終わっていない記録。古い順に拾う。 */
    List<DailyLog> findByStatusOrderByIdAsc(SanitizeStatus status, Pageable pageable);

    long countByStatus(SanitizeStatus status);
}
