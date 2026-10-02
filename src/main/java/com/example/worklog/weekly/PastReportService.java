package com.example.worklog.weekly;

import com.example.worklog.domain.PastWeeklyReport;
import com.example.worklog.domain.PastWeeklyReportRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 過去週報の取り込みと、そこから得た文体例の供給。 */
@Service
public class PastReportService {

    private static final Logger log = LoggerFactory.getLogger(PastReportService.class);

    /** 何週にも渡って同一の行は定型文とみなす閾値。 */
    private static final int BOILERPLATE_THRESHOLD = 3;

    public record ImportResult(int imported, int updated, int skipped) {

        public int total() {
            return imported + updated;
        }
    }

    private final WeeklyReportCsvParser parser;
    private final PastWeeklyReportRepository repository;

    public PastReportService(WeeklyReportCsvParser parser, PastWeeklyReportRepository repository) {
        this.parser = parser;
        this.repository = repository;
    }

    @Transactional
    public ImportResult importCsv(byte[] csvBytes) {
        int imported = 0;
        int updated = 0;
        int skipped = 0;

        for (WeeklyReportCsvParser.ParsedWeek w : parser.parse(csvBytes)) {
            var existing = repository.findByReportYearAndReportMonthAndWeekNum(w.year(), w.month(), w.weekNum());
            if (existing.isPresent()) {
                existing.get().update(w.performance(), w.communication(), w.learned());
                repository.save(existing.get());
                updated++;
            } else {
                repository.save(new PastWeeklyReport(w.year(), w.month(), w.weekNum(),
                        w.performance(), w.communication(), w.learned()));
                imported++;
            }
        }
        log.info("過去週報を取り込みました: 新規 {} 件 / 更新 {} 件", imported, updated);
        return new ImportResult(imported, updated, skipped);
    }

    @Transactional(readOnly = true)
    public List<PastWeeklyReport> all() {
        return repository.findAllByOrderByReportYearDescReportMonthDescWeekNumDesc();
    }

    @Transactional(readOnly = true)
    public long count() {
        return repository.count();
    }

    /**
     * 生成時に参照する文体例。
     *
     * <p>【業務遂行】の 1 文目は数パターンの定型文の使い回しであり、例として与えても
     * 意味が無い。定型文を除いた「その週固有の記述」だけを抽出する。
     *
     * <p>定型かどうかはキーワードではなく<b>出現回数</b>で判定する。何週にも渡って
     * 同一の行が現れるならそれは定型文であり、書き手が実際に使い回している証拠になる。
     * 語彙の変化に追従できるため、キーワード列挙より頑健。
     */
    @Transactional(readOnly = true)
    public List<String> distinctiveExamples(int limit) {
        List<PastWeeklyReport> reports = repository.findAllByOrderByReportYearDescReportMonthDescWeekNumDesc();

        Map<String, Integer> frequency = new LinkedHashMap<>();
        for (PastWeeklyReport r : reports) {
            for (String line : lines(r.getPerformance())) {
                frequency.merge(line, 1, Integer::sum);
            }
        }

        List<String> examples = new ArrayList<>();
        for (PastWeeklyReport r : reports) {
            for (String line : lines(r.getPerformance())) {
                if (frequency.getOrDefault(line, 0) < BOILERPLATE_THRESHOLD
                        && !examples.contains(line)) {
                    examples.add(line);
                    if (examples.size() >= limit) {
                        return examples;
                    }
                }
            }
        }
        return examples;
    }

    /** 使い回されている定型文。【業務遂行】1 文目の候補として提示できる。 */
    @Transactional(readOnly = true)
    public List<String> boilerplateCandidates() {
        Map<String, Integer> frequency = new LinkedHashMap<>();
        for (PastWeeklyReport r : repository.findAll()) {
            for (String line : lines(r.getPerformance())) {
                frequency.merge(line, 1, Integer::sum);
            }
        }
        return frequency.entrySet().stream()
                .filter(e -> e.getValue() >= BOILERPLATE_THRESHOLD)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<String> lines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
