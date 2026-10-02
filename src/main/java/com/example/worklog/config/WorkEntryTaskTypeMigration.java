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
 * 作業種別を単数から複数へ移行する。
 *
 * <p>work_entry.task_type_id（旧・単数の外部キー）を work_entry_task_type（新・連関テーブル）
 * へ移し、旧列を落とす。ddl-auto=update は列の削除もデータ移行も行わないため、
 * 既存の記録を失わないように明示的に処理する。
 *
 * <p>旧列が無ければ何もしないので、何度起動しても安全。
 * Flyway を導入した時点でこのクラスは移行スクリプトへ置き換える。
 */
@Configuration
public class WorkEntryTaskTypeMigration {

    private static final Logger log = LoggerFactory.getLogger(WorkEntryTaskTypeMigration.class);

    @Bean
    @Order(0)
    ApplicationRunner migrateWorkEntryTaskTypes(DataSource dataSource) {
        return args -> {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            if (!legacyColumnExists(jdbc)) {
                return;
            }
            Integer moved = jdbc.queryForObject(
                    "select count(*) from work_entry where task_type_id is not null", Integer.class);

            jdbc.update("""
                    insert into work_entry_task_type (work_entry_id, task_type_id, position)
                    select we.id, we.task_type_id, 0 from work_entry we
                     where we.task_type_id is not null
                       and not exists (select 1 from work_entry_task_type m where m.work_entry_id = we.id)
                    """);
            jdbc.execute("alter table work_entry drop column task_type_id");
            reorderSeededTaskTypes(jdbc);

            log.info("作業種別を複数対応へ移行しました。既存の記録 {} 件を引き継ぎました。", moved);
        };
    }

    /**
     * 既存インストールの作業種別マスタを作業の流れ順に並べ替える。
     *
     * <p>複数選択したときの連結順は画面の並び順（＝表示順）になるため、
     * 「PR作成/レビュー依頼」のように自然な順で出力されるようにしておく。
     * 新規インストールは投入時点でこの順になっている。
     * 移行時の 1 回だけ実行するので、以後の並べ替えを妨げない。
     */
    private void reorderSeededTaskTypes(JdbcTemplate jdbc) {
        String[] flow = {"調査", "チケット作成", "Issue作成", "動作確認", "PR作成", "レビュー依頼",
                "PR確認", "PR修正", "PRマージ", "リリース対応", "ジョブ確認", "バージョンアップ対応"};
        for (int i = 0; i < flow.length; i++) {
            jdbc.update("update task_type set display_order = ? where name = ?", i + 1, flow[i]);
        }
        log.info("作業種別マスタの表示順を作業の流れ順に整えました。");
    }

    private boolean legacyColumnExists(JdbcTemplate jdbc) {
        try {
            Integer count = jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                     where upper(table_name) = 'WORK_ENTRY' and upper(column_name) = 'TASK_TYPE_ID'
                    """, Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("旧列の有無を判定できませんでした。移行をスキップします: {}", e.getMessage());
            return false;
        }
    }
}
