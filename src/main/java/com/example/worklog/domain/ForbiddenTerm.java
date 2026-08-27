package com.example.worklog.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 社外に出してはならない固有名詞。
 * この辞書によるマスキングは決定論的で、LLM の判断を一切挟まない。
 */
@Entity
@Table(name = "forbidden_term",
        uniqueConstraints = @UniqueConstraint(columnNames = "term"))
public class ForbiddenTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String term;

    /** 置換後の一般名詞。空なら category のデフォルトを使う。 */
    @Column(nullable = false, length = 200)
    private String replacement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TermCategory category = TermCategory.OTHER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ForbiddenTerm() {
    }

    public ForbiddenTerm(String term, String replacement, TermCategory category) {
        this.term = term;
        this.category = category == null ? TermCategory.OTHER : category;
        this.replacement = (replacement == null || replacement.isBlank())
                ? this.category.getDefaultReplacement()
                : replacement;
    }

    public Long getId() {
        return id;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }

    public TermCategory getCategory() {
        return category;
    }

    public void setCategory(TermCategory category) {
        this.category = category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
