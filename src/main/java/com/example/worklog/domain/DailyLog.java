package com.example.worklog.domain;

import com.example.worklog.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 1日分の業務ログ。
 *
 * <p>rawText は客先の機密を含みうる一次情報のため、必ず暗号化して保存する。
 * 保持する理由は、辞書を改善したあとに過去ログを再処理できるようにするため。
 * 出力として参照してよいのは outputText のみ。
 */
@Entity
@Table(name = "daily_log", indexes = @Index(name = "idx_daily_log_work_date", columnList = "workDate"))
public class DailyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate workDate;

    /** 入力そのまま。AES-GCM で暗号化して保存される。 */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, length = 20000)
    private String rawText;

    /** 辞書マスキング直後のテキスト。LLM への入力はこれ。 */
    @Column(nullable = false, length = 10000)
    private String maskedText;

    /** 最終出力。週報生成や職務経歴の材料として参照するのはこの列のみ。 */
    @Column(nullable = false, length = 10000)
    private String outputText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SanitizeStatus status;

    /** 入力時に辞書で置換した件数。0 件が続く場合は辞書の登録漏れを疑う。 */
    @Column(nullable = false)
    private int maskedCount;

    /** 辞書未登録だが固有名詞の可能性がある語（RiskScanner の検出結果）。カンマ区切り。 */
    @Column(length = 2000)
    private String riskHints;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected DailyLog() {
    }

    public DailyLog(LocalDate workDate, String rawText, String maskedText, String outputText,
                    SanitizeStatus status, int maskedCount, String riskHints) {
        this.workDate = workDate;
        this.rawText = rawText;
        this.maskedText = maskedText;
        this.outputText = outputText;
        this.status = status;
        this.maskedCount = maskedCount;
        this.riskHints = riskHints;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public String getRawText() {
        return rawText;
    }

    public String getMaskedText() {
        return maskedText;
    }

    public String getOutputText() {
        return outputText;
    }

    public SanitizeStatus getStatus() {
        return status;
    }

    public int getMaskedCount() {
        return maskedCount;
    }

    public String getRiskHints() {
        return riskHints;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
