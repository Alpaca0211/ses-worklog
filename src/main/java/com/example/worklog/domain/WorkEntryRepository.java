package com.example.worklog.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkEntryRepository extends JpaRepository<WorkEntry, Long> {

    List<WorkEntry> findByWorkDateBetweenOrderByWorkDateAscProjectDisplayOrderAscIdAsc(LocalDate from, LocalDate to);

    List<WorkEntry> findByWorkDateOrderByIdAsc(LocalDate workDate);
}
