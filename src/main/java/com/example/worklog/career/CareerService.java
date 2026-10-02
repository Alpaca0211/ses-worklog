package com.example.worklog.career;

import com.example.worklog.domain.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * スキルシートの下書きを組み立てる。
 *
 * <p>期間・稼動月数・担当業務の件数・経験年数はすべて記録の集計で求める（決定論的処理）。
 * 文章化が要るのは「取り組み・実績」だけで、そこに限って LLM を使う。
 */
@Service
public class CareerService {

    private final WorkEntryRepository workEntryRepository;
    private final DailyLogRepository dailyLogRepository;
    private final PastWeeklyReportRepository pastReportRepository;
    private final ProjectRepository projectRepository;
    private final TechnologyRepository technologyRepository;
    private final AchievementGenerator achievementGenerator;

    public CareerService(WorkEntryRepository workEntryRepository,
                         DailyLogRepository dailyLogRepository,
                         PastWeeklyReportRepository pastReportRepository,
                         ProjectRepository projectRepository,
                         TechnologyRepository technologyRepository,
                         AchievementGenerator achievementGenerator) {
        this.workEntryRepository = workEntryRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.pastReportRepository = pastReportRepository;
        this.projectRepository = projectRepository;
        this.technologyRepository = technologyRepository;
        this.achievementGenerator = achievementGenerator;
    }

    @Transactional(readOnly = true)
    public List<Project> projects() {
        return projectRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Technology> technologies() {
        return technologyRepository.findAllByOrderByCategoryAscDisplayOrderAscIdAsc();
    }

    /** 案件を追加する。社外向けの項目は追加後に編集する。 */
    @Transactional
    public Project addProject(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("案件名が空です。");
        }
        return projectRepository.findAll().stream()
                .filter(p -> trimmed.equals(p.getName()))
                .findFirst()
                .orElseGet(() -> projectRepository.save(
                        new Project(trimmed, projectRepository.findAll().size() + 1)));
    }

    /** 技術を追加する。表示順はその区分の末尾。 */
    @Transactional
    public Technology addTechnology(String name, TechnologyCategory category) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("技術名が空です。");
        }
        if (category == null) {
            throw new IllegalArgumentException("区分を選んでください。");
        }
        return technologyRepository.findAll().stream()
                .filter(t -> trimmed.equalsIgnoreCase(t.getName()))
                .findFirst()
                .orElseGet(() -> {
                    int order = (int) technologyRepository.findAll().stream()
                            .filter(t -> t.getCategory() == category).count() + 1;
                    return technologyRepository.save(new Technology(trimmed, category, order));
                });
    }

    /**
     * 技術の有効・無効を切り替える。
     *
     * <p>無効にしても選択済みの案件からは外れない。経験年数にも引き続き算入される。
     * 使わなくなった技術を選択肢から隠すだけで、過去の実績は消さない。
     */
    @Transactional
    public void toggleTechnology(Long id) {
        technologyRepository.findById(id).ifPresent(t -> {
            t.setActive(!t.isActive());
            technologyRepository.save(t);
        });
    }

    @Transactional
    public void updateProject(Long projectId, ProjectDetails details) {
        projectRepository.findById(projectId).ifPresent(p -> {
            p.setIndustry(trim(details.industry()));
            p.setPublicDescription(trim(details.publicDescription()));
            p.setOverview(trim(details.overview()));
            p.setTeamComposition(trim(details.teamComposition()));
            p.setProjectScale(trim(details.projectScale()));
            p.setEnvironment(trim(details.environment()));
            p.setStartDate(details.startDate());
            p.setEndDate(details.endDate());
            p.setTechnologies(details.technologyIds() == null ? List.of()
                    : technologyRepository.findAllById(details.technologyIds()));
            projectRepository.save(p);
        });
    }

    /** 画面から受け取る案件の詳細。 */
    public record ProjectDetails(String industry, String publicDescription, String overview,
                                 String teamComposition, String projectScale, String environment,
                                 LocalDate startDate, LocalDate endDate, List<Long> technologyIds) {
    }

    /**
     * @param generateAchievements 実績の文章を LLM で生成するか。
     *                             集計だけ見たい場合に待たされないよう分けている
     */
    @Transactional(readOnly = true)
    public CareerDraft build(LocalDate from, LocalDate to, boolean generateAchievements) {
        List<WorkEntry> entries = workEntryRepository
                .findByWorkDateBetweenOrderByWorkDateAscProjectDisplayOrderAscIdAsc(from, to);
        Map<Project, List<WorkEntry>> byProject = entries.stream()
                .collect(Collectors.groupingBy(WorkEntry::getProject, LinkedHashMap::new, Collectors.toList()));

        List<CareerEntry> result = new ArrayList<>();
        for (Map.Entry<Project, List<WorkEntry>> group : byProject.entrySet()) {
            result.add(toEntry(group.getKey(), group.getValue(), from, to));
        }

        // 日次メモは案件に紐付かないため、取り組みは期間全体で 1 回だけ生成する
        List<String> achievements = generateAchievements
                ? achievementGenerator.generate(materials(from, to))
                : List.of();

        return new CareerDraft(result, achievements, aggregateTechnologyExperience());
    }

    private CareerEntry toEntry(Project project, List<WorkEntry> list, LocalDate from, LocalDate to) {
        LocalDate start = resolveStart(project, list, from);
        LocalDate end = resolveEnd(project, list, to);
        return new CareerEntry(
                project.getIndustry(),
                project.getPublicLabel(),
                project.getOverview(),
                project.getTeamComposition(),
                project.getProjectScale(),
                project.getEnvironment(),
                start,
                end,
                monthsBetween(start, end),
                list.size(),
                countTaskTypes(list),
                project.getTechnologies().stream().map(Technology::getName).toList());
    }

    /** 明示された期間を優先する。作業記録を持たない過去案件を登録できるようにするため。 */
    private LocalDate resolveStart(Project project, List<WorkEntry> list, LocalDate fallback) {
        if (project.getStartDate() != null) {
            return project.getStartDate();
        }
        return list.stream().map(WorkEntry::getWorkDate).min(Comparator.naturalOrder()).orElse(fallback);
    }

    private LocalDate resolveEnd(Project project, List<WorkEntry> list, LocalDate fallback) {
        if (project.getEndDate() != null) {
            return project.getEndDate();
        }
        return list.stream().map(WorkEntry::getWorkDate).max(Comparator.naturalOrder()).orElse(fallback);
    }

    /**
     * 技能歴。その技術を使った案件の期間を合計する。
     *
     * <p>表示期間では絞らない。経験年数は積み上げであり、
     * 表示の都合で値が変わってはならないため。
     */
    @Transactional(readOnly = true)
    public List<TechnologyExperience> aggregateTechnologyExperience() {
        Map<Technology, Integer> totals = new LinkedHashMap<>();
        for (Project project : projectRepository.findAll()) {
            int months = projectMonths(project);
            if (months <= 0) {
                continue;
            }
            for (Technology tech : project.getTechnologies()) {
                totals.merge(tech, months, Integer::sum);
            }
        }
        return totals.entrySet().stream()
                .map(e -> new TechnologyExperience(e.getKey().getName(), e.getKey().getCategory(), e.getValue()))
                .sorted(Comparator.comparing(TechnologyExperience::category)
                        .thenComparing(Comparator.comparingInt(TechnologyExperience::months).reversed()))
                .toList();
    }

    /** 案件の稼動月数。明示された期間が無ければ作業記録の範囲から求める。 */
    private int projectMonths(Project project) {
        LocalDate start = project.getStartDate();
        LocalDate end = project.getEndDate();
        if (start == null || end == null) {
            List<WorkEntry> all = workEntryRepository.findByProjectOrderByWorkDateAsc(project);
            if (all.isEmpty()) {
                return 0;
            }
            if (start == null) {
                start = all.get(0).getWorkDate();
            }
            if (end == null) {
                end = all.get(all.size() - 1).getWorkDate();
            }
        }
        return monthsBetween(start, end);
    }

    /** 実績生成の材料。日次メモの生ログと、取り込んだ過去週報の記述。 */
    private List<String> materials(LocalDate from, LocalDate to) {
        List<String> materials = new ArrayList<>();
        dailyLogRepository.findByWorkDateBetweenOrderByWorkDateAsc(from, to)
                .forEach(l -> materials.add(l.getRawText()));

        pastReportRepository.findAll().stream()
                .filter(r -> withinRange(r, from, to))
                .forEach(r -> {
                    materials.add(r.getPerformance());
                    materials.add(r.getLearned());
                });
        return materials;
    }

    /** 過去週報は日付を持たないため、年月で期間に重なるかを判定する。 */
    private boolean withinRange(PastWeeklyReport r, LocalDate from, LocalDate to) {
        int ym = r.getReportYear() * 100 + r.getReportMonth();
        return ym >= from.getYear() * 100 + from.getMonthValue()
                && ym <= to.getYear() * 100 + to.getMonthValue();
    }

    /** 月単位の通算。シートに合わせて開始月と終了月の両方を含める。 */
    private int monthsBetween(LocalDate start, LocalDate end) {
        return (int) ChronoUnit.MONTHS.between(start.withDayOfMonth(1), end.withDayOfMonth(1)) + 1;
    }

    private List<CareerEntry.TaskCount> countTaskTypes(List<WorkEntry> entries) {
        return entries.stream()
                .flatMap(e -> e.getTaskTypes().stream().map(TaskType::getName))
                .collect(Collectors.groupingBy(n -> n, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new CareerEntry.TaskCount(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();
    }

    private String trim(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
