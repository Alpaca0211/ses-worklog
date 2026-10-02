package com.example.worklog.domain;

import jakarta.persistence.*;

/**
 * 技能歴に挙げる技術。案件に紐付けることで経験年数を自動集計する。
 *
 * <p>年数を手で積み上げると案件が増えるたびに全技術を数え直すことになり、
 * 更新漏れも起きる。案件期間の合計として機械的に出す。
 */
@Entity
@Table(name = "technology", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Technology {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TechnologyCategory category;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected Technology() {
    }

    public Technology(String name, TechnologyCategory category, int displayOrder) {
        this.name = name;
        this.category = category;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public TechnologyCategory getCategory() {
        return category;
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
