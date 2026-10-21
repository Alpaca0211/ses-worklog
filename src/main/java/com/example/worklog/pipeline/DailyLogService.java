package com.example.worklog.pipeline;

import com.example.worklog.domain.DailyLog;
import com.example.worklog.domain.DailyLogRepository;
import com.example.worklog.domain.EvaluationItem;
import com.example.worklog.domain.EvaluationItemRepository;
import com.example.worklog.domain.SanitizeStatus;
import com.example.worklog.masking.RiskScanner.RiskHint;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * これ以下の長さの入力は抽象化しない。
     * 走り書きを報告向けの文体へ直すのが抽象化の目的であり、既に短く整った一行には
     * 変換すべき余地が無い。むしろ情報が少ないほど LLM は書かれていない内容を補う。
     */
    private final int minAbstractLength;

    public DailyLogService(SanitizePipeline pipeline, DailyLogRepository repository,
                           EvaluationItemRepository evaluationItemRepository,
                           ApplicationEventPublisher events,
                           @Value("${worklog.llm.min-abstract-length:60}") int minAbstractLength) {
        this.minAbstractLength = minAbstractLength;
        this.pipeline = pipeline;
        this.repository = repository;
        this.evaluationItemRepository = evaluationItemRepository;
        this.events = events;
    }

    /** 抽象化を通すべきか。短文は通さない。 */
    private boolean worthAbstracting(String rawText) {
        return rawText != null && rawText.strip().length() > minAbstractLength;
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
        boolean abstractable = worthAbstracting(rawText);
        DailyLog entity = new DailyLog(
                workDate,
                rawText,
                result.maskedText(),
                result.outputText(),
                abstractable ? result.status() : SanitizeStatus.ABSTRACTION_SKIPPED,
                result.maskedCount(),
                joinHints(result.riskHints()));
        if (evaluationItemIds != null && !evaluationItemIds.isEmpty()) {
            entity.setEvaluationItems(evaluationItemRepository.findAllById(evaluationItemIds));
        }
        DailyLog saved = repository.save(entity);
        if (abstractable) {
            events.publishEvent(new DailyLogRecorded(saved.getId()));
        }
        return saved;
    }

    /**
     * 本文を書き直す。秘匿化は最初からやり直す。
     * 打ち間違いや、あとから思い出した内容を足せないと記録が続かないため。
     */
    @Transactional
    public DailyLog update(Long id, String rawText) {
        DailyLog entity = find(id);
        SanitizedResult result = pipeline.mask(rawText);
        boolean abstractable = worthAbstracting(rawText);
        entity.rewrite(rawText, result.maskedText(), result.outputText(),
                abstractable ? result.status() : SanitizeStatus.ABSTRACTION_SKIPPED,
                result.maskedCount(), joinHints(result.riskHints()));
        DailyLog saved = repository.save(entity);
        if (abstractable) {
            events.publishEvent(new DailyLogRecorded(saved.getId()));
        }
        return saved;
    }

    /** 記録を消す。評価項目の紐づけも併せて外す。 */
    @Transactional
    public void delete(Long id) {
        DailyLog entity = find(id);
        entity.setEvaluationItems(List.of());
        repository.save(entity);
        repository.delete(entity);
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
