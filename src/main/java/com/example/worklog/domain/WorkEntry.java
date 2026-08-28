package com.example.worklog.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    /** 施策名。例:「脆弱性対応（8月）」。案件内のワークストリームにあたる。 */
    @Column(length = 200)
    private String workstream;

    /**
     * 作業種別。同一対象に対して複数のアクションを行う日があるため複数持てる。
     * 出力時は「PR作成/レビュー依頼」のように連結する。
     * 順序を保持するのは、実際の記法が作業の流れ順に並んでいるため。
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "work_entry_task_type",
            joinColumns = @JoinColumn(name = "work_entry_id"),
            inverseJoinColumns = @JoinColumn(name = "task_type_id"))
    @OrderColumn(name = "position")
    private List<TaskType> taskTypes = new ArrayList<>();

    /** 対象。例:「service_a：stg、service_b：stg」。リポジトリ名は数が多く変動するため自由記述。 */
    @Column(length = 500)
    private String targets;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected WorkEntry() {
    }

    public WorkEntry(LocalDate workDate, Project project, String workstream,
                     List<TaskType> taskTypes, String targets) {
        this.workDate = workDate;
        this.project = project;
        this.workstream = workstream;
        this.taskTypes = new ArrayList<>(taskTypes);
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

    public List<TaskType> getTaskTypes() {
        return taskTypes;
    }

    /** 画面表示用。「PR作成/レビュー依頼」の形。 */
    public String getTaskTypeLabel() {
        return taskTypes.stream().map(TaskType::getName).reduce((a, b) -> a + "/" + b).orElse("");
    }

    public String getTargets() {
        return targets;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
