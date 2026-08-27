package com.example.worklog.weekly;

import com.example.worklog.domain.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 定型作業の記録。マスタからの選択が中心で、LLM は使わない。 */
@Service
public class WorkEntryService {

    private final WorkEntryRepository workEntryRepository;
    private final ProjectRepository projectRepository;
    private final TaskTypeRepository taskTypeRepository;

    public WorkEntryService(WorkEntryRepository workEntryRepository,
                            ProjectRepository projectRepository,
                            TaskTypeRepository taskTypeRepository) {
        this.workEntryRepository = workEntryRepository;
        this.projectRepository = projectRepository;
        this.taskTypeRepository = taskTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<Project> projects() {
        return projectRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<TaskType> taskTypes() {
        return taskTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<WorkEntry> entriesOn(LocalDate date) {
        return workEntryRepository.findByWorkDateOrderByIdAsc(date);
    }

    @Transactional
    public WorkEntry add(LocalDate workDate, Long projectId, String workstream,
                         Long taskTypeId, String targets) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("案件が見つかりません: " + projectId));
        TaskType taskType = taskTypeRepository.findById(taskTypeId)
                .orElseThrow(() -> new IllegalArgumentException("作業種別が見つかりません: " + taskTypeId));
        return workEntryRepository.save(
                new WorkEntry(workDate, project, trim(workstream), taskType, trim(targets)));
    }

    @Transactional
    public void delete(Long id) {
        workEntryRepository.deleteById(id);
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
