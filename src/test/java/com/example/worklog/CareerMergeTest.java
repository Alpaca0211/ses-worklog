package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.worklog.career.CareerDraft;
import com.example.worklog.career.CareerEntry;
import com.example.worklog.career.CareerService;
import com.example.worklog.career.TechnologyExperience;
import com.example.worklog.domain.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * スキルシート上の案件統合。
 *
 * <p>実際には 1 つの案件が機能ごとに分かれて動いている場合、週報では機能ごとの
 * 区切りが要るので案件は分けたまま、スキルシートでは 1 ブロックにまとめる。
 */
class CareerMergeTest extends SanitizeTestBase {

    @Autowired
    CareerService service;

    @Autowired
    ProjectRepository projects;

    @Autowired
    TaskTypeRepository taskTypes;

    @Autowired
    TechnologyRepository technologies;

    @Autowired
    WorkEntryRepository workEntries;

    private Project main;
    private Project sub;
    private TaskType release;
    private Technology java;

    @BeforeEach
    void setUp() {
        workEntries.deleteAll();
        projects.deleteAll();
        technologies.deleteAll();
        taskTypes.deleteAll();

        release = taskTypes.save(new TaskType("リリース対応", 1));
        java = technologies.save(new Technology("Java", TechnologyCategory.LANGUAGE_FRAMEWORK, 1));

        main = projects.save(new Project("機能A", 1));
        sub = projects.save(new Project("機能B", 2));
    }

    private void record(Project p, String date) {
        workEntries.save(new WorkEntry(LocalDate.parse(date), p, "対応", List.of(release), "x"));
    }

    private void mergeSubIntoMain() {
        service.setCareerMergeInto(sub.getId(), main.getId());
    }

    @Test
    void 統合すると業務実績が1ブロックにまとまる() {
        record(main, "2026-08-10");
        record(sub, "2026-08-11");
        mergeSubIntoMain();

        CareerDraft draft = service.build(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), false);

        assertThat(draft.entries()).hasSize(1);
        assertThat(draft.entries().get(0).totalItems()).isEqualTo(2);
    }

    @Test
    void 統合しなければ案件ごとに分かれる() {
        record(main, "2026-08-10");
        record(sub, "2026-08-11");

        CareerDraft draft = service.build(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), false);

        assertThat(draft.entries()).hasSize(2);
    }

    @Test
    void 統合した案件の作業種別は合算される() {
        record(main, "2026-08-10");
        record(sub, "2026-08-11");
        record(sub, "2026-08-12");
        mergeSubIntoMain();

        CareerDraft draft = service.build(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), false);

        assertThat(draft.entries().get(0).taskCounts())
                .extracting(CareerEntry.TaskCount::name, CareerEntry.TaskCount::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("リリース対応", 3L));
    }

    @Test
    void 経験年数は統合先でまとめて数えるので重複しない() {
        // 機能ごとに分かれた案件は稼動期間が重なる。案件ごとに合計すると期間が倍になる
        main.setStartDate(LocalDate.parse("2025-09-01"));
        main.setEndDate(LocalDate.parse("2026-08-31"));
        main.setTechnologies(List.of(java));
        projects.save(main);
        sub.setStartDate(LocalDate.parse("2025-09-01"));
        sub.setEndDate(LocalDate.parse("2026-08-31"));
        sub.setTechnologies(List.of(java));
        projects.save(sub);
        mergeSubIntoMain();

        List<TechnologyExperience> result = service.aggregateTechnologyExperience();

        // 12ヶ月。2案件分で24ヶ月にはならない
        assertThat(result).hasSize(1);
        assertThat(result.get(0).duration()).isEqualTo("1年0ヶ月");
    }

    @Test
    void 統合先の期間は束ねた全案件の作業記録から求める() {
        record(main, "2026-08-10");
        record(sub, "2026-10-20");
        main.setTechnologies(List.of(java));
        projects.save(main);
        mergeSubIntoMain();

        // 8月〜10月で 3ヶ月。統合先の記録だけを見ると 1ヶ月になってしまう
        assertThat(service.aggregateTechnologyExperience().get(0).duration()).isEqualTo("0年3ヶ月");
    }

    @Test
    void 作業記録が無くても期間を明示した案件は業務実績に出る() {
        // 記録を取り始める前の案件はスキルシートに必要で、作業記録からは拾えない
        Project past = projects.save(new Project("過去案件", 9));
        past.setStartDate(LocalDate.parse("2024-01-01"));
        past.setEndDate(LocalDate.parse("2024-06-30"));
        projects.save(past);

        CareerDraft draft = service.build(LocalDate.parse("2024-01-01"), LocalDate.parse("2026-12-31"), false);

        assertThat(draft.entries()).extracting(CareerEntry::periodRange)
                .contains("2024年1月 〜 2024年6月");
    }

    @Test
    void 期間が範囲外の案件は出ない() {
        Project past = projects.save(new Project("過去案件", 9));
        past.setStartDate(LocalDate.parse("2020-01-01"));
        past.setEndDate(LocalDate.parse("2020-06-30"));
        projects.save(past);

        CareerDraft draft = service.build(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), false);

        assertThat(draft.entries()).isEmpty();
    }

    @Test
    void 業務実績は新しい順に並ぶ() {
        Project old = projects.save(new Project("古い案件", 9));
        old.setStartDate(LocalDate.parse("2024-01-01"));
        old.setEndDate(LocalDate.parse("2024-06-30"));
        projects.save(old);
        record(main, "2026-08-10");

        CareerDraft draft = service.build(LocalDate.parse("2024-01-01"), LocalDate.parse("2026-12-31"), false);

        assertThat(draft.entries()).extracting(CareerEntry::start)
                .containsExactly(LocalDate.parse("2026-08-10"), LocalDate.parse("2024-01-01"));
    }

    @Test
    void 自分自身は統合先にできない() {
        assertThatThrownBy(() -> service.setCareerMergeInto(sub.getId(), sub.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 統合は1段までで連鎖させない() {
        mergeSubIntoMain();
        Project third = projects.save(new Project("機能C", 3));

        assertThatThrownBy(() -> service.setCareerMergeInto(third.getId(), sub.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1段");
    }

    @Test
    void 統合を解除できる() {
        record(main, "2026-08-10");
        record(sub, "2026-08-11");
        mergeSubIntoMain();
        service.setCareerMergeInto(sub.getId(), null);

        CareerDraft draft = service.build(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), false);

        assertThat(draft.entries()).hasSize(2);
    }
}
