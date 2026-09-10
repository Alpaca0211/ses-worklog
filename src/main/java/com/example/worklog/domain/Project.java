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

    /**
     * 社外向けの案件説明。職務経歴では案件名を伏せるため、
     * 「担当案件」が複数並んで区別が付かなくなるのを防ぐ。
     * 例:「Webサービスの保守開発」。未設定なら「担当案件」と表示される。
     */
    @Column(length = 200)
    private String publicDescription;

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

    public String getPublicDescription() {
        return publicDescription;
    }

    public void setPublicDescription(String publicDescription) {
        this.publicDescription = publicDescription;
    }

    /** 社外向けの表示名。説明が未設定なら伏せた既定値を返す。 */
    public String getPublicLabel() {
        return (publicDescription == null || publicDescription.isBlank())
                ? "担当案件" : publicDescription.trim();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
