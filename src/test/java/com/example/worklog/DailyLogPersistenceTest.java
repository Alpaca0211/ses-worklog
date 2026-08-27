package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.domain.DailyLog;
import com.example.worklog.domain.TermCategory;
import com.example.worklog.masking.TermService;
import com.example.worklog.pipeline.DailyLogService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DailyLogPersistenceTest extends SanitizeTestBase {

    @Autowired
    DailyLogService logService;

    @Autowired
    TermService termService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        termService.add("ヴェガ銀行", "顧客企業", TermCategory.CLIENT);
    }

    @Test
    void 生ログは暗号化されて保存され復号して読み出せる() {
        DailyLog saved = logService.record(LocalDate.of(2026, 8, 27), "ヴェガ銀行の勘定系で障害調査");

        // アプリ経由では平文で読める
        assertThat(logService.find(saved.getId()).getRawText()).isEqualTo("ヴェガ銀行の勘定系で障害調査");

        // DB 上は暗号文であり、禁止用語が平文で残っていない
        String stored = jdbcTemplate.queryForObject(
                "select raw_text from daily_log where id = ?", String.class, saved.getId());
        assertThat(stored).startsWith("enc:v1:");
        assertThat(stored).doesNotContain("ヴェガ銀行");
    }

    @Test
    void 出力列には禁止用語が含まれない() {
        DailyLog saved = logService.record(LocalDate.of(2026, 8, 27), "ヴェガ銀行の定例に参加");

        String output = jdbcTemplate.queryForObject(
                "select output_text from daily_log where id = ?", String.class, saved.getId());
        assertThat(output).doesNotContain("ヴェガ銀行").contains("顧客企業");
    }
}
