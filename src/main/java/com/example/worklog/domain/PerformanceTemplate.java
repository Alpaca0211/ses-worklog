package com.example.worklog.domain;

import jakarta.persistence.*;

/**
 * 【業務遂行】の 1 文目に使う定型表現。
 *
 * <p>過去 4 年分の実績では、1 文目は数パターンの使い回しであり、
 * 同一文が何週も連続する。生成する意味が無いため選択式にする。
 */
@Entity
@Table(name = "performance_template")
public class PerformanceTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false)
    private int displayOrder;

    protected PerformanceTemplate() {
    }

    public PerformanceTemplate(String text, int displayOrder) {
        this.text = text;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
