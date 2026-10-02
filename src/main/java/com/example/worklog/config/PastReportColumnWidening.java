package com.example.worklog.config;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * past_weekly_report の本文列を広げる。
 *
 * <p>当初 2000 字で作成したが、実データには 2000 字を超える記述があり
 * 取り込みが丸ごとロールバックしていた。ddl-auto=update は既存列の型を
 * 変更しないため、明示的に広げる。
 *
 * <p>すでに十分な幅があれば何もしない。Flyway を導入した時点で移行スクリプトへ置き換える。
 */
@Configuration
public class PastReportColumnWidening {

    private static final Logger log = LoggerFactory.getLogger(PastReportColumnWidening.class);

    private static final int REQUIRED_LENGTH = 20000;
    private static final String[] COLUMNS = {"PERFORMANCE", "COMMUNICATION", "LEARNED"};

    @Bean
    @Order(1)
    ApplicationRunner widenPastReportColumns(DataSource dataSource) {
        return args -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            for (String column : COLUMNS) {
                Integer length = currentLength(jdbc, column);
                if (length == null || length >= REQUIRED_LENGTH) {
                    continue;
                }
                try {
                    jdbc.execute("alter table past_weekly_report alter column " + column
                            + " varchar(" + REQUIRED_LENGTH + ")");
                    log.info("past_weekly_report.{} を {} 字へ広げました（旧: {}）",
                            column, REQUIRED_LENGTH, length);
                } catch (Exception e) {
                    log.warn("past_weekly_report.{} の拡張に失敗しました: {}", column, e.getMessage());
                }
            }
        };
    }

    private Integer currentLength(JdbcTemplate jdbc, String column) {
        try {
            return jdbc.queryForObject("""
                    select max(character_maximum_length) from information_schema.columns
                     where upper(table_name) = 'PAST_WEEKLY_REPORT' and upper(column_name) = ?
                    """, Integer.class, column);
        } catch (Exception e) {
            return null;
        }
    }
}
