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
                                     PerformanceTemplateRepository templates,
                                     TechnologyRepository technologies) {
        return args -> {
            if (projects.count() == 0) {
                projects.saveAll(List.of(
                        new Project("案件A", 1),
                        new Project("案件B", 2),
                        new Project("案件C", 3)));
                log.info("案件マスタの雛形を投入しました。/work から自分の案件名に変更してください。");
            }
            if (taskTypes.count() == 0) {
                // 並び順は作業の流れ順。複数選択したときの連結順がこの順になるため、
                // 「PR作成/レビュー依頼」のように自然な順で出力される
                taskTypes.saveAll(List.of(
                        new TaskType("調査", 1),
                        new TaskType("チケット作成", 2),
                        new TaskType("Issue作成", 3),
                        new TaskType("動作確認", 4),
                        new TaskType("PR作成", 5),
                        new TaskType("レビュー依頼", 6),
                        new TaskType("PR確認", 7),
                        new TaskType("PR修正", 8),
                        new TaskType("PRマージ", 9),
                        new TaskType("リリース対応", 10),
                        new TaskType("ジョブ確認", 11),
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
            if (technologies.count() == 0) {
                // スキルシートの技能歴に挙げる候補。案件に紐付けると経験年数が自動で積み上がる
                seedTechnologies(technologies);
                log.info("技術マスタを投入しました: {} 件。/career から案件に紐付けてください。",
                        technologies.count());
            }
        };
    }

    /**
     * 技能歴に挙げる候補。案件に紐付けると、その案件の期間が経験年数へ積み上がる。
     * 過不足は画面から追加・無効化して調整する。
     */
    private void seedTechnologies(TechnologyRepository technologies) {
        String[] languages = {"Java", "Spring Boot", "Thymeleaf", "JSP", "JavaScript", "TypeScript",
                "jQuery", "React", "Vue.js", "Node.js", "HTML", "CSS", "Shell",
                "SQL", "PostgreSQL", "MySQL", "Oracle"};
        String[] tools = {"Windows", "Linux", "Git", "GitHub", "GitLab", "SVN",
                "Eclipse", "IntelliJ IDEA", "Docker", "AWS", "Jenkins", "Redmine"};

        int order = 1;
        for (String name : languages) {
            technologies.save(new Technology(name, TechnologyCategory.LANGUAGE_FRAMEWORK, order++));
        }
        order = 1;
        for (String name : tools) {
            technologies.save(new Technology(name, TechnologyCategory.OS_TOOL, order++));
        }
    }
}
