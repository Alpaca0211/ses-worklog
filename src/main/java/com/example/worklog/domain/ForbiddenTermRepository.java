package com.example.worklog.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForbiddenTermRepository extends JpaRepository<ForbiddenTerm, Long> {

    List<ForbiddenTerm> findByEnabledTrue();

    List<ForbiddenTerm> findAllByOrderByCategoryAscTermAsc();

    Optional<ForbiddenTerm> findByTerm(String term);

    boolean existsByTerm(String term);
}
