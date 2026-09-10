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
 * 職務経歴エントリの組み立て。
 *
 * <p>期間と件数は作業記録を集計して求める（決定論的処理）。
 * 文章化が要るのは「取り組み・実績」だけで、そこに限って LLM を使う。
 */
@Service
public class CareerService {

    private final WorkEntryRepository workEntryRepository;
    private final DailyLogRepository dailyLogRepository;
    private final PastWeeklyReportRepository pastReportRepository;
    private final ProjectRepository projectRepository;
    private final AchievementGenerator achievementGenerator;

    public CareerService(WorkEntryRepository workEntryRepository,
                         DailyLogRepository dailyLogRepository,
                         PastWeeklyReportRepository pastReportRepository,
                         ProjectRepository projectRepository,
                         AchievementGenerator achievementGenerator) {
        this.workEntryRepository = workEntryRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.pastReportRepository = pastReportRepository;
        this.projectRepository = projectRepository;
        this.achievementGenerator = achievementGenerator;
    }

    @Transactional(readOnly = true)
    public List<Project> projects() {
        return projectRepository.findAll();
    }

    @Transactional
    public void describe(Long projectId, String description) {
        projectRepository.findById(projectId).ifPresent(p -> {
            p.setPublicDescription(description == null ? null : description.trim());
            projectRepository.save(p);
        });
    }

    /**
     * 期間内の作業記録を案件ごとにまとめる。
     *
     * @param generateAchievements 実績の文章を LLM で生成するか。
     *                             集計だけ見たい場合に待たされないよう分けている
     */
    @Transactional(readOnly = true)
    public CareerDraft build(LocalDate from, LocalDate to, boolean generateAchievements) {
        List<WorkEntry> entries =
                workEntryRepository.findByWorkDateBetweenOrderByWorkDateAscProjectDisplayOrderAscIdAsc(from, to);
        if (entries.isEmpty()) {
            return new CareerDraft(List.of(), List.of());
        }
        Map<Project, List<WorkEntry>> byProject = entries.stream()
                .collect(Collectors.groupingBy(WorkEntry::getProject, LinkedHashMap::new, Collectors.toList()));

        List<CareerEntry> result = new ArrayList<>();
        for (Map.Entry<Project, List<WorkEntry>> group : byProject.entrySet()) {
            List<WorkEntry> list = group.getValue();
            LocalDate start = list.stream().map(WorkEntry::getWorkDate).min(Comparator.naturalOrder()).orElse(from);
            LocalDate end = list.stream().map(WorkEntry::getWorkDate).max(Comparator.naturalOrder()).orElse(to);

            result.add(new CareerEntry(
                    group.getKey().getPublicLabel(),
                    start,
                    end,
                    monthsBetween(start, end),
                    list.size(),
                    countTaskTypes(list)));
        }
        // 日次メモは案件に紐付かないため、取り組みは期間全体で 1 回だけ生成する
        List<String> achievements = generateAchievements
                ? achievementGenerator.generate(materials(from, to))
                : List.of();
        return new CareerDraft(result, achievements);
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
}
