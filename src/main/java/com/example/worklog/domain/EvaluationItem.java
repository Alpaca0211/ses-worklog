package com.example.worklog.domain;

import jakarta.persistence.*;

/**
 * 人事評価の項目。
 *
 * <p>日次メモに紐付けることで、評価期間ごとに「どの項目に材料が溜まっていて、
 * どの項目が空か」が分かる。期末に材料を探すのではなく、期中に不足へ気付くための仕組み。
 */
@Entity
@Table(name = "evaluation_item", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class EvaluationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected EvaluationItem() {
    }

    public EvaluationItem(String name, int displayOrder) {
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
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
