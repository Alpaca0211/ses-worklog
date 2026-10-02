package com.example.worklog.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 過去の週報から取り込んだ記録。
 *
 * <p>用途は 2 つ。生成時の文体・粒度の参照（few-shot 例）と、職務経歴の材料。
 * 私生活の記述とリーダーからの返信は取り込まない。
 */
@Entity
@Table(name = "past_weekly_report",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reportYear", "reportMonth", "weekNum"}))
public class PastWeeklyReport {

    /**
     * 各欄の上限。実データには 2000 字を超える記述があったため広めに取る。
     * 変更する場合は {@code PastReportColumnWidening} の値も合わせること。
     */
    static final int COLUMN_LENGTH = 20000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // year / month は多くの DB で予約語のため、列名に使わない
    @Column(nullable = false)
    private int reportYear;

    @Column(nullable = false)
    private int reportMonth;

    @Column(nullable = false)
    private int weekNum;

    /** 【業務遂行】 */
    @Column(length = COLUMN_LENGTH)
    private String performance;

    /** 【報連相】 */
    @Column(length = COLUMN_LENGTH)
    private String communication;

    /** 直近で学んだこと、覚えたこと */
    @Column(length = COLUMN_LENGTH)
    private String learned;

    @Column(nullable = false)
    private Instant importedAt = Instant.now();

    protected PastWeeklyReport() {
    }

    public PastWeeklyReport(int reportYear, int reportMonth, int weekNum,
                            String performance, String communication, String learned) {
        this.reportYear = reportYear;
        this.reportMonth = reportMonth;
        this.weekNum = weekNum;
        update(performance, communication, learned);
    }

    public void update(String performance, String communication, String learned) {
        this.performance = performance;
        this.communication = communication;
        this.learned = learned;
        this.importedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public int getReportYear() {
        return reportYear;
    }

    public int getReportMonth() {
        return reportMonth;
    }

    public int getWeekNum() {
        return weekNum;
    }

    public String getPerformance() {
        return performance;
    }

    public String getCommunication() {
        return communication;
    }

    public String getLearned() {
        return learned;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public String getLabel() {
        return reportYear + "年" + reportMonth + "月" + weekNum + "週";
    }
}
