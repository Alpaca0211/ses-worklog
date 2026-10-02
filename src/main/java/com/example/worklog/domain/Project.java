package com.example.worklog.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 案件。週報の作業内容欄で「▶ 案件名」のグルーピング単位になり、
 * スキルシートの業務実績 1 ブロックにも対応する。
 *
 * <p>{@code name} は社内でしか使わない識別名。社外に出る職務経歴・スキルシートでは
 * {@code publicDescription} 以降の項目だけを使い、{@code name} は出さない。
 */
@Entity
@Table(name = "project", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 社内での識別名。入力時の選択肢に出る。社外には出さない。 */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    // --- ここから下はスキルシート・職務経歴に出る項目 ---

    /** 業種。例:「金融」「小売業」「通信」 */
    @Column(length = 50)
    private String industry;

    /**
     * 社外向けの案件名。スキルシートの ≪…≫ に入る。
     * 例:「融資管理システム金利計算対応」。客先企業名は入れない。
     */
    @Column(length = 200)
    private String publicDescription;

    /** [概要]。何のシステムで、何をする案件かを数行で。 */
    @Column(length = 2000)
    private String overview;

    /** [チーム構成]。例:「PL(1名)、メンバー(自身含む4名)」 */
    @Column(length = 200)
    private String teamComposition;

    /** [規模]。例:「プロジェクト画面数:約10、担当画面数:10」。該当しなければ空。 */
    @Column(length = 200)
    private String projectScale;

    /** 環境。例:「Windows / Linux」 */
    @Column(length = 100)
    private String environment;

    /**
     * 期間。明示されていればこれを使い、無ければ作業記録の日付範囲から求める。
     * 作業記録を持たない過去案件を登録できるようにするため、明示指定を優先する。
     */
    @Column
    private LocalDate startDate;

    @Column
    private LocalDate endDate;

    /** 使用技術。経験年数はこの紐付けと案件期間から集計する。 */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "project_technology",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "technology_id"))
    private List<Technology> technologies = new ArrayList<>();

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

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getPublicDescription() {
        return publicDescription;
    }

    public void setPublicDescription(String publicDescription) {
        this.publicDescription = publicDescription;
    }

    /** 社外向けの表示名。未設定なら伏せた既定値を返す。 */
    public String getPublicLabel() {
        return (publicDescription == null || publicDescription.isBlank())
                ? "担当案件" : publicDescription.trim();
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public String getTeamComposition() {
        return teamComposition;
    }

    public void setTeamComposition(String teamComposition) {
        this.teamComposition = teamComposition;
    }

    public String getProjectScale() {
        return projectScale;
    }

    public void setProjectScale(String projectScale) {
        this.projectScale = projectScale;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public List<Technology> getTechnologies() {
        return technologies;
    }

    public void setTechnologies(List<Technology> technologies) {
        this.technologies = new ArrayList<>(technologies);
    }

    /** 画面でのチェック状態の判定用。エンティティの同一性に依存せず id で比較する。 */
    public boolean usesTechnology(Long technologyId) {
        return technologyId != null
                && technologies.stream().anyMatch(t -> technologyId.equals(t.getId()));
    }
}
