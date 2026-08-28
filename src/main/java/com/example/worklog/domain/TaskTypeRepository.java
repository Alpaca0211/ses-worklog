package com.example.worklog.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTypeRepository extends JpaRepository<TaskType, Long> {

    List<TaskType> findByActiveTrueOrderByDisplayOrderAscIdAsc();
}
