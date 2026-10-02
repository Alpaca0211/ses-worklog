package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.career.CareerService;
import com.example.worklog.career.TechnologyExperience;
import com.example.worklog.domain.Project;
import com.example.worklog.domain.ProjectRepository;
import com.example.worklog.domain.Technology;
import com.example.worklog.domain.TechnologyCategory;
import com.example.worklog.domain.TechnologyRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 技能歴の経験年数は、その技術を使った案件の期間を合計して求める。
 *
 * <p>手で積み上げると案件が増えるたびに全技術を数え直すことになり更新漏れが起きる。
 * 集計で出すことにした以上、合算の仕方をテストで固定しておく。
 */
class TechnologyExperienceTest extends SanitizeTestBase {

    @Autowired
    CareerService service;

    @Autowired
    ProjectRepository projects;

    @Autowired
    TechnologyRepository technologies;

    private Technology java;
    private Technology git;

    @BeforeEach
    void setUp() {
        // 案件を先に消すと連関も消えるため、技術はその後に消す
        projects.deleteAll();
        technologies.deleteAll();

        java = technologies.save(new Technology("Java", TechnologyCategory.LANGUAGE_FRAMEWORK, 1));
        git = technologies.save(new Technology("Git", TechnologyCategory.OS_TOOL, 1));
    }

    private Project project(String name, String start, String end, Technology... used) {
        Project p = new Project(name, 1);
        p.setStartDate(start == null ? null : LocalDate.parse(start));
        p.setEndDate(end == null ? null : LocalDate.parse(end));
        p.setTechnologies(List.of(used));
        return projects.save(p);
    }

    @Test
    void 複数案件にまたがる技術は期間が合算される() {
        project("A", "2024-01-15", "2024-06-20", java, git); // 6ヶ月
        project("B", "2025-01-01", "2025-03-31", java);      // 3ヶ月

        List<TechnologyExperience> result = service.aggregateTechnologyExperience();

        assertThat(result).extracting(TechnologyExperience::name, TechnologyExperience::duration)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Java", "0年9ヶ月"),
                        org.assertj.core.groups.Tuple.tuple("Git", "0年6ヶ月"));
    }

    @Test
    void 月数は開始月と終了月の両方を含めて数える() {
        // シートの表記に合わせる。2024年1月〜2024年6月は 6ヶ月
        project("A", "2024-01-31", "2024-06-01", java);

        assertThat(service.aggregateTechnologyExperience())
                .first().extracting(TechnologyExperience::duration).isEqualTo("0年6ヶ月");
    }

    @Test
    void 一年を超える場合は年と月に分けて表記する() {
        project("A", "2023-01-01", "2025-02-28", java); // 26ヶ月

        assertThat(service.aggregateTechnologyExperience())
                .first().extracting(TechnologyExperience::duration).isEqualTo("2年2ヶ月");
    }

    @Test
    void 期間も作業記録も無い案件は加算されない() {
        project("A", null, null, java);

        assertThat(service.aggregateTechnologyExperience()).isEmpty();
    }

    @Test
    void 使われていない技術は技能歴に出ない() {
        project("A", "2024-01-01", "2024-03-31", java);

        assertThat(service.aggregateTechnologyExperience())
                .extracting(TechnologyExperience::name).containsExactly("Java");
    }

    @Test
    void 区分は技術ごとに保持される() {
        project("A", "2024-01-01", "2024-03-31", java, git);

        assertThat(service.aggregateTechnologyExperience())
                .extracting(TechnologyExperience::category)
                .containsExactlyInAnyOrder(
                        TechnologyCategory.LANGUAGE_FRAMEWORK, TechnologyCategory.OS_TOOL);
    }
}
