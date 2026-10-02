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

    /**
     * スキルシート上の統合先を設定する。
     * 統合先がさらに別へ統合されている場合は、連鎖させず拒否する。
     */
    @Transactional
    public void setCareerMergeInto(Long projectId, Long targetId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        if (targetId == null) {
            project.setCareerMergeInto(null);
            projectRepository.save(project);
            return;
        }
        if (targetId.equals(projectId)) {
            throw new IllegalArgumentException("自分自身は統合先にできません。");
        }
        Project target = projectRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("統合先が見つかりません。"));
        if (target.isMerged()) {
            throw new IllegalArgumentException("統合先がさらに別の案件へ統合されています。統合は1段までです。");
        }
        project.setCareerMergeInto(target);
        projectRepository.save(project);
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
        // 統合先が指定されていればそちらに寄せる。実際には 1 つの案件が機能ごとに
        // 分かれている場合、スキルシートでは 1 ブロックとして出すため
        Map<Project, List<WorkEntry>> byProject = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getProject().careerTarget(),
                        LinkedHashMap::new, Collectors.toList()));

        List<CareerEntry> result = new ArrayList<>();
        for (Map.Entry<Project, List<WorkEntry>> group : byProject.entrySet()) {
            result.add(toEntry(group.getKey(), group.getValue(), from, to));
        }

        // 作業記録を持たないが期間を明示した案件（過去案件）も業務実績に出す。
        // 記録を取り始める前の案件はスキルシートに必要で、作業記録からは拾えないため
        for (Project p : projectRepository.findAll()) {
            if (p.isMerged() || p.getStartDate() == null || byProject.containsKey(p)) {
                continue;
            }
            LocalDate end = p.getEndDate() == null ? to : p.getEndDate();
            boolean overlaps = !p.getStartDate().isAfter(to) && !end.isBefore(from);
            if (overlaps) {
                result.add(toEntry(p, List.of(), from, to));
            }
        }

        // シートの並びに合わせて新しい順にする
        result.sort(Comparator.comparing(CareerEntry::start).reversed());

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
        // 統合先に寄せてから数える。同じ技術を機能ごとの案件へ紐付けたときに、
        // 同一期間を案件の数だけ重複して積み上げないため
        for (Project project : projectRepository.findAll()) {
            if (project.isMerged()) {
                continue;
            }
            int months = mergedMonths(project);
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

    /**
     * 統合先の稼動月数。明示された期間が無ければ、束ねた全案件の作業記録から求める。
     * 機能ごとに分かれた案件は稼動期間が重なるため、合計ではなく全体の範囲で数える。
     */
    private int mergedMonths(Project target) {
        LocalDate start = target.getStartDate();
        LocalDate end = target.getEndDate();
        if (start != null && end != null) {
            return monthsBetween(start, end);
        }
        List<WorkEntry> all = new ArrayList<>();
        for (Project p : projectRepository.findAll()) {
            if (p.careerTarget().getId() != null && p.careerTarget().getId().equals(target.getId())) {
                all.addAll(workEntryRepository.findByProjectOrderByWorkDateAsc(p));
            }
        }
        if (all.isEmpty()) {
            return (start != null && end != null) ? monthsBetween(start, end) : 0;
        }
        LocalDate min = all.stream().map(WorkEntry::getWorkDate).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = all.stream().map(WorkEntry::getWorkDate).max(Comparator.naturalOrder()).orElseThrow();
        return monthsBetween(start == null ? min : start, end == null ? max : end);
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
