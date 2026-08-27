package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.domain.SanitizeStatus;
import com.example.worklog.domain.TermCategory;
import com.example.worklog.masking.TermService;
import com.example.worklog.pipeline.SanitizePipeline;
import com.example.worklog.pipeline.SanitizedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SanitizePipelineTest extends SanitizeTestBase {

    @Autowired
    SanitizePipeline pipeline;

    @Autowired
    TermService termService;

    @BeforeEach
    void setUp() {
        termService.add("オリオン物流", "顧客企業", TermCategory.CLIENT);
        termService.add("OrionWMS", "担当システム", TermCategory.SYSTEM);
    }

    @Test
    void LLMが無くても辞書マスキングだけで完結する() {
        SanitizedResult r = pipeline.sanitize("オリオン物流のOrionWMSでバッチ遅延が発生。原因はインデックス不足。");

        assertThat(r.status()).isEqualTo(SanitizeStatus.MASKED_ONLY);
        assertThat(r.outputText()).isEqualTo(r.maskedText());
        assertThat(r.maskedCount()).isEqualTo(2);
    }

    @Test
    void 最終出力に禁止用語が残らない() {
        SanitizedResult r = pipeline.sanitize("オリオン物流の件でOrionWMSを確認");

        assertThat(r.outputText()).doesNotContain("オリオン物流").doesNotContain("OrionWMS");
    }

    @Test
    void 辞書未登録の固有名詞候補は検出されるがマスクはされない() {
        SanitizedResult r = pipeline.sanitize("担当者の山田さんに連絡。連絡先は yamada@example.com");

        assertThat(r.riskHints())
                .extracting(h -> h.value())
                .contains("山田", "yamada@example.com");
        // 検出しただけで置換はしない（マスクの主体はあくまで辞書）
        assertThat(r.outputText()).contains("山田さん");
    }
}
