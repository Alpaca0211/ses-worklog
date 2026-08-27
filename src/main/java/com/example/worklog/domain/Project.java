package com.example.worklog.domain;

import jakarta.persistence.*;

/** 案件。週報の作業内容欄で「▶ 案件名」のグルーピング単位になる。 */
@Entity
@Table(name = "project", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected Project() {
    }

    public Project(String name, int displayOrder) {
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
