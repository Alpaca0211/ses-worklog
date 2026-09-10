package com.example.worklog.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 別の DB から現在の DB へデータを移す。H2 から PostgreSQL への移行に使う。
 *
 * <p>起動時に接続元を指定したときだけ動く。
 * <pre>
 * mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=postgres ^
 *   -Dspring-boot.run.arguments=--worklog.copy-from.url=jdbc:h2:file:./data/worklog
 * </pre>
 *
 * <p>列名を固定せず {@code select *} の結果から組み立てるため、
 * エンティティを変更しても追従する。移行先の表が空のときだけ実行するので、
 * 誤って二重に流し込むことはない。
 *
 * <p>暗号化された列は暗号文のまま複写する。鍵が同じであれば復号できる。
 */
@Configuration
@ConditionalOnProperty("worklog.copy-from.url")
public class DataCopyRunner {

    private static final Logger log = LoggerFactory.getLogger(DataCopyRunner.class);

    /** 外部キーの依存順。参照される側から先に複写する。 */
    private static final List<String> TABLES = List.of(
            "forbidden_term",
            "project",
            "task_type",
            "performance_template",
            "past_weekly_report",
            "daily_log",
            "work_entry",
            "work_entry_task_type");

    /** 自動採番を持つ表。複写後に次の採番値を最大 id の次へ送る。 */
    private static final List<String> IDENTITY_TABLES = List.of(
            "forbidden_term", "project", "task_type",
            "performance_template", "past_weekly_report", "daily_log", "work_entry");

    @Bean
    @Order(100)
    ApplicationRunner copyData(DataSource target,
                               @Value("${worklog.copy-from.url}") String sourceUrl,
                               @Value("${worklog.copy-from.username:sa}") String sourceUser,
                               @Value("${worklog.copy-from.password:}") String sourcePassword) {
        return args -> {
            JdbcTemplate jdbc = new JdbcTemplate(target);
            log.info("データ複写を開始します: {} → 現在の接続先", sourceUrl);

            try (Connection source = DriverManager.getConnection(sourceUrl, sourceUser, sourcePassword)) {
                for (String table : TABLES) {
                    copyTable(source, jdbc, table);
                }
                for (String table : IDENTITY_TABLES) {
                    restartIdentity(jdbc, table);
                }
            }
            log.info("データ複写が完了しました。");
        };
    }

    private void copyTable(Connection source, JdbcTemplate target, String table) {
        Integer existing = count(target, table);
        if (existing == null) {
            log.warn("{}: 移行先に表がありません。読み飛ばします。", table);
            return;
        }
        if (existing > 0) {
            log.info("{}: 移行先に {} 件あるため読み飛ばします。", table, existing);
            return;
        }

        try (Statement st = source.createStatement();
             ResultSet rs = st.executeQuery("select * from " + table)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            String sql = "insert into " + table + " (" + String.join(", ", columns) + ") values ("
                    + String.join(", ", columns.stream().map(c -> "?").toList()) + ")";

            int copied = 0;
            while (rs.next()) {
                Object[] values = new Object[columnCount];
                for (int i = 1; i <= columnCount; i++) {
                    values[i - 1] = rs.getObject(i);
                }
                target.update(sql, values);
                copied++;
            }
            log.info("{}: {} 件を複写しました。", table, copied);
        } catch (SQLException e) {
            throw new IllegalStateException(table + " の複写に失敗しました: " + e.getMessage(), e);
        }
    }

    /**
     * 自動採番の次の値を送る。
     * id を明示して挿入したため、送らないと次の登録で主キー重複になる。
     */
    private void restartIdentity(JdbcTemplate target, String table) {
        Integer max = target.queryForObject("select coalesce(max(id), 0) from " + table, Integer.class);
        int next = (max == null ? 0 : max) + 1;
        try {
            target.execute("alter table " + table + " alter column id restart with " + next);
            log.info("{}: 次の採番値を {} にしました。", table, next);
        } catch (Exception e) {
            log.warn("{}: 採番値の調整に失敗しました。手動で確認してください: {}", table, e.getMessage());
        }
    }

    /** 表が無ければ null を返す。移行先にまだ作られていない表を読み飛ばすため。 */
    private Integer count(JdbcTemplate target, String table) {
        try {
            return target.queryForObject("select count(*) from " + table, Integer.class);
        } catch (Exception e) {
            return null;
        }
    }
}
