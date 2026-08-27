package com.example.worklog.config;

import com.example.worklog.domain.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 初回起動時のみ、案件・作業種別・定型文のマスタを投入する。
 *
 * <p>案件名は利用者ごとに異なるためプレースホルダを入れる。
 * 作業種別と定型文は保守案件で一般的に使われる表現を初期値にしてある。
 */
@Configuration
public class MasterDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(MasterDataSeeder.class);

    @Bean
    ApplicationRunner seedMasterData(ProjectRepository projects,
                                     TaskTypeRepository taskTypes,
                                     PerformanceTemplateRepository templates) {
        return args -> {
            if (projects.count() == 0) {
                projects.saveAll(List.of(
                        new Project("案件A", 1),
                        new Project("案件B", 2),
                        new Project("案件C", 3)));
                log.info("案件マスタの雛形を投入しました。/work から自分の案件名に変更してください。");
            }
            if (taskTypes.count() == 0) {
                taskTypes.saveAll(List.of(
                        new TaskType("リリース対応", 1),
                        new TaskType("レビュー依頼", 2),
                        new TaskType("PR作成", 3),
                        new TaskType("PR確認", 4),
                        new TaskType("PRマージ", 5),
                        new TaskType("PR修正", 6),
                        new TaskType("チケット作成", 7),
                        new TaskType("Issue作成", 8),
                        new TaskType("動作確認", 9),
                        new TaskType("ジョブ確認", 10),
                        new TaskType("調査", 11),
                        new TaskType("バージョンアップ対応", 12)));
                log.info("作業種別マスタを投入しました: {} 件", taskTypes.count());
            }
            if (templates.count() == 0) {
                templates.saveAll(List.of(
                        new PerformanceTemplate("依頼された業務について、作業を滞りなく遂行することができている。", 1),
                        new PerformanceTemplate("各案件の対応業務において、滞りなく期限以内に対応を実施した。", 2),
                        new PerformanceTemplate("担当業務について、期限を守り安定して遂行できている。", 3)));
                log.info("【業務遂行】1文目の定型文を投入しました: {} 件", templates.count());
            }
        };
    }
}
