package com.example.worklog.domain;

import jakarta.persistence.*;

/**
 * 作業種別。「リリース対応」「レビュー依頼」など、繰り返し発生する定型作業のアクション。
 * マスタから選択させることで、手入力による表記ゆれと誤記の伝播を構造的に防ぐ。
 */
@Entity
@Table(name = "task_type", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class TaskType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected TaskType() {
    }

    public TaskType(String name, int displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
