package com.example.worklog.career;

import com.example.worklog.domain.DailyLog;
import com.example.worklog.domain.DailyLogRepository;
import com.example.worklog.domain.EvaluationItem;
import com.example.worklog.domain.EvaluationItemRepository;
import com.example.worklog.domain.PastWeeklyReportRepository;
import com.example.worklog.masking.MaskingProfile;
import com.example.worklog.masking.MaskingService;
import com.example.worklog.masking.RiskScanner;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * 評価期間。査定月までの半期を指す。
     * 期の途中で「何が足りないか」を見るための区切りなので、査定月から逆算する。
     */
    public record Period(String label, LocalDate from, LocalDate to) {
    }

    /** 期間全体の集計。 */
    public record EvaluationSummary(LocalDate from, LocalDate to,
                                    List<ItemMaterials> items,
                                    int untaggedCount,
                                    long weeklyReportCount,
                                    List<String> unregisteredTerms) {

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
    private final PastWeeklyReportRepository pastWeeklyReportRepository;
    private final RiskScanner riskScanner;

    public EvaluationService(DailyLogRepository dailyLogRepository,
                             EvaluationItemRepository evaluationItemRepository,
                             MaskingService maskingService,
                             PastWeeklyReportRepository pastWeeklyReportRepository,
                             RiskScanner riskScanner) {
        this.dailyLogRepository = dailyLogRepository;
        this.evaluationItemRepository = evaluationItemRepository;
        this.maskingService = maskingService;
        this.pastWeeklyReportRepository = pastWeeklyReportRepository;
        this.riskScanner = riskScanner;
    }

    /**
     * 評価期間の終わり月 -> それを査定する月。
     *
     * <p>査定は 6 月と 11 月に行われる。この 2 つで 1 年を過不足なく二分できるのは
     * 12 月〜5 月と 6 月〜11 月の組だけなので、それを既定とする。
     * 規定が異なる場合はこの対応表だけを直せばよい。
     */
    private static final Map<Integer, Integer> PERIOD_END_TO_REVIEW = Map.of(5, 6, 11, 11);

    /** 直近の評価期間を新しい順に返す。先頭が進行中の期。 */
    public List<Period> periods(LocalDate today) {
        YearMonth end = YearMonth.from(today);
        while (!PERIOD_END_TO_REVIEW.containsKey(end.getMonthValue())) {
            end = end.plusMonths(1);
        }
        List<Period> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            YearMonth e = end.minusMonths(6L * i);
            YearMonth b = e.minusMonths(5);
            result.add(new Period(
                    b + " 〜 " + e + "（" + PERIOD_END_TO_REVIEW.get(e.getMonthValue()) + "月査定）",
                    b.atDay(1), e.atEndOfMonth()));
        }
        return result;
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

        // 評価項目「週報」は日次メモではなく、提出した週報そのものが材料になる
        long weeklyReports = pastWeeklyReportRepository.countInMonthRange(
                YearMonth.from(from).getYear() * 100 + from.getMonthValue(),
                YearMonth.from(to).getYear() * 100 + to.getMonthValue());

        return new EvaluationSummary(from, to, result, untagged, weeklyReports,
                unregisteredTerms(logs));
    }

    /**
     * 材料に残っている、辞書未登録の固有名詞候補。
     *
     * <p>この画面は生入力を社内向けにマスクして出すため、出力の安全性は辞書だけが担保する。
     * 登録漏れがあると人名などがそのまま残るので、持ち出す前に気付けるようにする。
     */
    private List<String> unregisteredTerms(List<DailyLog> logs) {
        return logs.stream()
                .filter(l -> !l.isUntagged())
                .flatMap(l -> riskScanner.scan(internalText(l)).stream())
                .map(RiskScanner.RiskHint::value)
                .distinct()
                .sorted()
                .toList();
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
