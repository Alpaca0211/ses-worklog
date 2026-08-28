package com.example.worklog.weekly;

import com.example.worklog.domain.*;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 週報 1 週分を組み立てる。LLM を使うのは【業務遂行】2 文目だけ。 */
@Service
public class WeeklyReportService {

    private final WorkEntryRepository workEntryRepository;
    private final DailyLogRepository dailyLogRepository;
    private final PerformanceTemplateRepository templateRepository;
    private final WorkContentFormatter formatter;
    private final PerformanceGenerator performanceGenerator;

    public WeeklyReportService(WorkEntryRepository workEntryRepository,
                               DailyLogRepository dailyLogRepository,
                               PerformanceTemplateRepository templateRepository,
                               WorkContentFormatter formatter,
                               PerformanceGenerator performanceGenerator) {
        this.workEntryRepository = workEntryRepository;
        this.dailyLogRepository = dailyLogRepository;
        this.templateRepository = templateRepository;
        this.formatter = formatter;
        this.performanceGenerator = performanceGenerator;
    }

    @Transactional(readOnly = true)
    public List<PerformanceTemplate> templates() {
        return templateRepository.findAllByOrderByDisplayOrderAscIdAsc();
    }

    /**
     * @param generateSecond 2 文目を LLM で生成するか。既定では生成せず、
     *                       利用者が明示的に要求したときだけ呼ぶ（毎回待たせないため）
     */
    @Transactional(readOnly = true)
    public WeeklyReport build(ReportWeek week, Long templateId, boolean generateSecond) {
        List<WorkEntry> entries = workEntryRepository
                .findByWorkDateBetweenOrderByWorkDateAscProjectDisplayOrderAscIdAsc(week.start(), week.end());

        Map<LocalDate, List<WorkEntry>> byDate = entries.stream()
                .collect(Collectors.groupingBy(WorkEntry::getWorkDate, LinkedHashMap::new, Collectors.toList()));

        List<WeeklyReport.DailyContent> days = new ArrayList<>();
        for (LocalDate d = week.start(); !d.isAfter(week.end()); d = d.plusDays(1)) {
            String label = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.JAPAN);
            days.add(new WeeklyReport.DailyContent(d, label, formatter.formatDay(byDate.get(d))));
        }

        String first = templateRepository.findById(templateId == null ? -1L : templateId)
                .map(PerformanceTemplate::getText)
                .orElse("");

        String second = null;
        if (generateSecond) {
            List<DailyLog> logs = dailyLogRepository
                    .findByWorkDateBetweenOrderByWorkDateAsc(week.start(), week.end());
            second = performanceGenerator.generateSecondSentence(logs).orElse(null);
        }

        return new WeeklyReport(week, days, summarize(week, entries), first, second);
    }

    private WeeklySummary summarize(ReportWeek week, List<WorkEntry> entries) {
        // 1 記録が複数の作業種別を持ちうるため、種別の合計は記録件数を上回ることがある
        List<WeeklySummary.CountItem> byTaskType = tally(entries.stream()
                .flatMap(e -> e.getTaskTypes().stream().map(TaskType::getName)));
        List<WeeklySummary.CountItem> byProject = tally(entries.stream()
                .map(e -> e.getProject().getName()));
        int activeDays = (int) entries.stream().map(WorkEntry::getWorkDate).distinct().count();
        return new WeeklySummary(week, entries.size(), activeDays, byTaskType, byProject);
    }

    private List<WeeklySummary.CountItem> tally(java.util.stream.Stream<String> names) {
        return names
                .collect(Collectors.groupingBy(n -> n, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new WeeklySummary.CountItem(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();
    }
}
