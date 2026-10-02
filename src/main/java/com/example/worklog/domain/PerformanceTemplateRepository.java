package com.example.worklog.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceTemplateRepository extends JpaRepository<PerformanceTemplate, Long> {

    List<PerformanceTemplate> findAllByOrderByDisplayOrderAscIdAsc();
}
