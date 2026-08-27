package com.example.worklog.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 定型作業の記録。1 行が週報の作業内容欄の「・」1 項目に対応する。
 *
 * <p>保守対応は同種の作業が繰り返されるため、毎回文章を書かせるのではなく
 * 案件と作業種別をマスタから選ばせる。LLM は一切関与しない。
 */
@Entity
@Table(name = "work_entry", indexes = @Index(name = "idx_work_entry_date", columnList = "workDate"))
public class WorkEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate workDate;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** 施策名。例:「PVM脆弱性対応（8月）」。案件内のワークストリームにあたる。 */
    @Column(length = 200)
    private String workstream;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "task_type_id", nullable = false)
    private TaskType taskType;

    /** 対象。例:「feed_api：stg、content_api：stg」。リポジトリ名は数が多く変動するため自由記述。 */
    @Column(length = 500)
    private String targets;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected WorkEntry() {
    }

    public WorkEntry(LocalDate workDate, Project project, String workstream,
                     TaskType taskType, String targets) {
        this.workDate = workDate;
        this.project = project;
        this.workstream = workstream;
        this.taskType = taskType;
        this.targets = targets;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public Project getProject() {
        return project;
    }

    public String getWorkstream() {
        return workstream;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public String getTargets() {
        return targets;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
