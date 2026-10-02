package com.example.worklog.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnologyRepository extends JpaRepository<Technology, Long> {

    List<Technology> findByActiveTrueOrderByCategoryAscDisplayOrderAscIdAsc();

    List<Technology> findAllByOrderByCategoryAscDisplayOrderAscIdAsc();
}
