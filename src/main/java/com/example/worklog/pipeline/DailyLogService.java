package com.example.worklog.pipeline;

import com.example.worklog.domain.DailyLog;
import com.example.worklog.domain.DailyLogRepository;
import com.example.worklog.masking.RiskScanner.RiskHint;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyLogService {

    private final SanitizePipeline pipeline;
    private final DailyLogRepository repository;

    public DailyLogService(SanitizePipeline pipeline, DailyLogRepository repository) {
        this.pipeline = pipeline;
        this.repository = repository;
    }

    /** 秘匿化して保存する。戻り値は保存されたログ。 */
    @Transactional
    public DailyLog record(LocalDate workDate, String rawText) {
        SanitizedResult result = pipeline.sanitize(rawText);
        DailyLog entity = new DailyLog(
                workDate,
                rawText,
                result.maskedText(),
                result.outputText(),
                result.status(),
                result.maskedCount(),
                joinHints(result.riskHints()));
        return repository.save(entity);
    }

    /** 保存せずに秘匿化結果だけ確認する（入力画面のプレビュー用）。 */
    public SanitizedResult preview(String rawText) {
        return pipeline.sanitize(rawText);
    }

    @Transactional(readOnly = true)
    public List<DailyLog> recent(int limit) {
        return repository.findAllByOrderByWorkDateDescIdDesc(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public DailyLog find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ログが見つかりません: " + id));
    }

    private String joinHints(List<RiskHint> hints) {
        if (hints == null || hints.isEmpty()) {
            return null;
        }
        String joined = hints.stream()
                .map(h -> h.type() + ":" + h.value())
                .distinct()
                .collect(Collectors.joining(", "));
        return joined.length() > 1990 ? joined.substring(0, 1990) : joined;
    }
}
