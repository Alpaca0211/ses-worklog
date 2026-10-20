package com.example.worklog.career;

import com.example.worklog.domain.DailyLog;
import com.example.worklog.domain.DailyLogRepository;
import com.example.worklog.domain.EvaluationItem;
import com.example.worklog.domain.EvaluationItemRepository;
import com.example.worklog.masking.MaskingProfile;
import com.example.worklog.masking.MaskingService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 評価期間ごとに、項目別の材料を集める。
 *
 * <p>評価は期末に材料を探しても作れない。期中に「材料が無い項目」が見えることが目的で、
 * 文章を生成することではない。そのため LLM は使わず、記録をそのまま並べる。
 */
@Service
public class EvaluationService {

    /** 1 項目分の材料。 */
    public record ItemMaterials(String itemName, List<Material> materials) {

        public boolean isEmpty() {
            return materials.isEmpty();
        }

        public int count() {
            return materials.size();
        }

        /** 項目ごとにまとめてコピーできる形。 */
        public String asText() {
            return materials.stream()
                    .map(m -> "・" + m.text())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        }
    }

    public record Material(LocalDate date, String text) {
    }

    /** 期間全体の集計。 */
    public record EvaluationSummary(LocalDate from, LocalDate to,
                                    List<ItemMaterials> items,
                                    int untaggedCount) {

        /** 材料が無い項目。期中に手を打つべき対象。 */
        public List<String> emptyItems() {
            return items.stream().filter(ItemMaterials::isEmpty).map(ItemMaterials::itemName).toList();
        }

        public boolean hasEmptyItems() {
            return !emptyItems().isEmpty();
        }
    }

    private final DailyLogRepository dailyLogRepository;
    private final EvaluationItemRepository evaluationItemRepository;
    private final MaskingService maskingService;

    public EvaluationService(DailyLogRepository dailyLogRepository,
                             EvaluationItemRepository evaluationItemRepository,
                             MaskingService maskingService) {
        this.dailyLogRepository = dailyLogRepository;
        this.evaluationItemRepository = evaluationItemRepository;
        this.maskingService = maskingService;
    }

    @Transactional(readOnly = true)
    public List<EvaluationItem> items() {
        return evaluationItemRepository.findAllByOrderByDisplayOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public EvaluationSummary summarize(LocalDate from, LocalDate to) {
        List<DailyLog> logs = dailyLogRepository.findByWorkDateBetweenOrderByWorkDateAsc(from, to);

        List<ItemMaterials> result = new ArrayList<>();
        for (EvaluationItem item : evaluationItemRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()) {
            List<Material> materials = logs.stream()
                    .filter(l -> l.taggedWith(item.getId()))
                    .map(l -> new Material(l.getWorkDate(), internalText(l)))
                    .toList();
            result.add(new ItemMaterials(item.getName(), materials));
        }
        int untagged = (int) logs.stream().filter(DailyLog::isUntagged).count();
        return new EvaluationSummary(from, to, result, untagged);
    }

    /**
     * 社内向けマスキングを掛けた本文。
     * 評価の材料は自社へ出すものなので案件名は残し、人名などは伏せる。
     */
    private String internalText(DailyLog log) {
        return maskingService.mask(log.getRawText(), MaskingProfile.INTERNAL).text().trim();
    }

    /** 分類がまだのメモ。後から付け直せるように一覧で返す。 */
    @Transactional(readOnly = true)
    public List<DailyLog> untagged(LocalDate from, LocalDate to) {
        return dailyLogRepository.findByWorkDateBetweenOrderByWorkDateAsc(from, to).stream()
                .filter(DailyLog::isUntagged)
                .toList();
    }
}
