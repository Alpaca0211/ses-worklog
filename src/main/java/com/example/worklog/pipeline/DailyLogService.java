package com.example.worklog.pipeline;

import com.example.worklog.domain.DailyLog;
import com.example.worklog.domain.DailyLogRepository;
import com.example.worklog.domain.EvaluationItem;
import com.example.worklog.domain.EvaluationItemRepository;
import com.example.worklog.masking.RiskScanner.RiskHint;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyLogService {

    private final SanitizePipeline pipeline;
    private final DailyLogRepository repository;
    private final EvaluationItemRepository evaluationItemRepository;
    private final ApplicationEventPublisher events;

    public DailyLogService(SanitizePipeline pipeline, DailyLogRepository repository,
                           EvaluationItemRepository evaluationItemRepository,
                           ApplicationEventPublisher events) {
        this.pipeline = pipeline;
        this.repository = repository;
        this.evaluationItemRepository = evaluationItemRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<EvaluationItem> evaluationItems() {
        return evaluationItemRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
    }

    /** 既存のメモに評価項目を付け直す。記録を取り始める前のメモを後から分類するため。 */
    @Transactional
    public void tag(Long logId, List<Long> evaluationItemIds) {
        DailyLog log = find(logId);
        log.setEvaluationItems(evaluationItemIds == null ? List.of()
                : evaluationItemRepository.findAllById(evaluationItemIds));
        repository.save(log);
    }

    /**
     * 秘匿化して保存する。戻り値は保存されたログ。
     *
     * <p>保存は辞書マスキング（決定論的・即時）だけで確定させる。LLM による抽象化は
     * コミット後に {@link AbstractionBacklog} が追いかける。細かい記録ほど件数が多く、
     * 1 件ごとに LLM の往復を待たせると記録が続かないため。
     */
    @Transactional
    public DailyLog record(LocalDate workDate, String rawText, List<Long> evaluationItemIds) {
        SanitizedResult result = pipeline.mask(rawText);
        DailyLog entity = new DailyLog(
                workDate,
                rawText,
                result.maskedText(),
                result.outputText(),
                result.status(),
                result.maskedCount(),
                joinHints(result.riskHints()));
        if (evaluationItemIds != null && !evaluationItemIds.isEmpty()) {
            entity.setEvaluationItems(evaluationItemRepository.findAllById(evaluationItemIds));
        }
        DailyLog saved = repository.save(entity);
        events.publishEvent(new DailyLogRecorded(saved.getId()));
        return saved;
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
