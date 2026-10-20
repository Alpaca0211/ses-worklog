package com.example.worklog.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationItemRepository extends JpaRepository<EvaluationItem, Long> {

    List<EvaluationItem> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    List<EvaluationItem> findAllByOrderByDisplayOrderAscIdAsc();
}
