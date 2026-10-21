package com.example.worklog.pipeline;

import com.example.worklog.domain.DailyLog;
import com.example.worklog.domain.DailyLogRepository;
import com.example.worklog.domain.SanitizeStatus;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 抽象化の後追い処理。
 *
 * <p>保存を待たせない理由: 細かい記録ほど件数が多く、1 件あたり数秒の LLM 往復を
 * 同期で挟むと記録そのものが続かない。秘匿化に必要な辞書マスキングは決定論的で
 * 即時に終わるため、保存を確定させるにはそれだけで足りる。
 *
 * <p>LLM が止まっていた期間の記録は MASKED_ONLY のまま残るので、あとから拾い直せる。
 */
@Service
public class AbstractionBacklog {

    private static final Logger log = LoggerFactory.getLogger(AbstractionBacklog.class);

    private final SanitizePipeline pipeline;
    private final DailyLogRepository repository;
    private final TransactionTemplate tx;

    public AbstractionBacklog(SanitizePipeline pipeline, DailyLogRepository repository,
                              PlatformTransactionManager transactionManager) {
        this.pipeline = pipeline;
        this.repository = repository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 保存のコミット後に動かす。コミット前に動かすと別スレッドから行が見えない。 */
    @Async("abstractionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecorded(DailyLogRecorded event) {
        abstractOne(event.logId());
    }

    /**
     * 1 件だけ抽象化する。失敗しても MASKED_ONLY のまま残すだけで記録は壊さない。
     *
     * <p>LLM の往復はトランザクションの外で行う。数秒かかる処理のあいだ
     * DB 接続を掴み続けないため。
     */
    public boolean abstractOne(Long logId) {
        String maskedText = tx.execute(s -> repository.findById(logId)
                .filter(l -> l.getStatus() == SanitizeStatus.MASKED_ONLY)
                .map(DailyLog::getMaskedText)
                .orElse(null));
        if (maskedText == null) {
            return false;
        }

        Optional<SanitizePipeline.Abstracted> attempt;
        try {
            attempt = pipeline.abstractAndGate(maskedText);
        } catch (RuntimeException e) {
            log.warn("抽象化に失敗しました（記録は辞書マスキングのみで保持）: id={}", logId, e);
            return false;
        }
        if (attempt.isEmpty()) {
            return false;
        }
        final SanitizePipeline.Abstracted abstracted = attempt.get();

        return Boolean.TRUE.equals(tx.execute(s -> repository.findById(logId)
                .map(l -> {
                    if (l.getStatus() != SanitizeStatus.MASKED_ONLY) {
                        return false;
                    }
                    l.applyAbstraction(abstracted.outputText(), abstracted.status());
                    repository.save(l);
                    return true;
                })
                .orElse(false)));
    }

    /** 未抽象化のまとめ直し。LLM を止めていた期間の記録を拾うため。 */
    public int abstractPending(int limit) {
        List<Long> ids = tx.execute(s -> repository
                .findByStatusOrderByIdAsc(SanitizeStatus.MASKED_ONLY, PageRequest.of(0, limit))
                .stream()
                .map(DailyLog::getId)
                .toList());
        int done = 0;
        for (Long id : ids == null ? List.<Long>of() : ids) {
            if (abstractOne(id)) {
                done++;
            }
        }
        return done;
    }

    public long pendingCount() {
        return repository.countByStatus(SanitizeStatus.MASKED_ONLY);
    }
}
