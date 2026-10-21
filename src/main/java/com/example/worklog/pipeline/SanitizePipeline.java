package com.example.worklog.pipeline;

import com.example.worklog.abstraction.AbstractionService;
import com.example.worklog.domain.SanitizeStatus;
import com.example.worklog.masking.MaskingResult;
import com.example.worklog.masking.MaskingService;
import com.example.worklog.masking.RiskScanner;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 秘匿化パイプライン。
 *
 * <pre>
 *   生入力
 *     ↓ ① 辞書による決定論的マスキング   … 必須・LLM 非依存・即時
 *     ↓ ② ローカルLLM による文脈的抽象化 … 任意・失敗しても縮退
 *     ↓ ③ 出力側での再マスキング         … 最終ゲート
 *     ↓ ④ 残存検査（アサーション）
 *   最終出力
 * </pre>
 *
 * <p>③ を置いている理由: LLM は② の入力（マスク済み）しか見ていないため原理的には
 * 禁止用語を出力できないが、few-shot 例の混入や辞書更新との競合で再出現しうる。
 * ③ は決定論的処理なので、通過後の出力に辞書用語が残らないことを保証できる。
 *
 * <p>① と ②〜④ を別メソッドに分けているのは、保存を ① だけで確定させるため。
 * ② は数秒かかるので、保存の待ち時間に含めると記録が続かない（{@link AbstractionBacklog}）。
 */
@Service
public class SanitizePipeline {

    private static final Logger log = LoggerFactory.getLogger(SanitizePipeline.class);

    private final MaskingService maskingService;
    private final AbstractionService abstractionService;
    private final RiskScanner riskScanner;

    public SanitizePipeline(MaskingService maskingService,
                            AbstractionService abstractionService,
                            RiskScanner riskScanner) {
        this.maskingService = maskingService;
        this.abstractionService = abstractionService;
        this.riskScanner = riskScanner;
    }

    /** ②〜④ を通した結果。 */
    public record Abstracted(String outputText, SanitizeStatus status) {
    }

    /**
     * ① のみ。決定論的なので即時に終わる。
     * 禁止用語の除去はここで完了しているため、保存を確定させるにはこれで足りる。
     */
    public SanitizedResult mask(String rawText) {
        MaskingResult masked = maskingService.mask(rawText);
        // 辞書未登録の固有名詞候補は、マスク後のテキストに対して検出する
        List<RiskScanner.RiskHint> hints = riskScanner.scan(masked.text());
        return new SanitizedResult(masked.text(), masked.text(), SanitizeStatus.MASKED_ONLY,
                masked.totalCount(), masked.hitCounts(), hints);
    }

    /** ②③④。LLM が使えなければ empty を返し、呼び出し側は ① のまま縮退する。 */
    public Optional<Abstracted> abstractAndGate(String maskedText) {
        // ② LLM による抽象化（任意）
        Optional<String> abstracted = abstractionService.abstractText(maskedText);
        if (abstracted.isEmpty()) {
            return Optional.empty();
        }

        // ③ 出力側の最終ゲート
        MaskingResult regated = maskingService.mask(abstracted.get());
        SanitizeStatus status = SanitizeStatus.ABSTRACTED;
        if (regated.totalCount() > 0) {
            log.warn("LLM 出力に禁止用語が再出現したため再マスキングしました: {}", regated.hitTerms());
            status = SanitizeStatus.ABSTRACTED_REMASKED;
        }

        // ④ 残存検査
        List<String> leaks = maskingService.detectLeaks(regated.text());
        if (!leaks.isEmpty()) {
            throw new IllegalStateException(
                    "秘匿化パイプラインの不具合: 最終出力に禁止用語が残存しています " + leaks);
        }

        return Optional.of(new Abstracted(regated.text(), status));
    }

    /** ①〜④ を通す。入力画面の秘匿化プレビューはこれを使う。 */
    public SanitizedResult sanitize(String rawText) {
        SanitizedResult masked = mask(rawText);
        return abstractAndGate(masked.maskedText())
                .map(a -> new SanitizedResult(masked.maskedText(), a.outputText(), a.status(),
                        masked.maskedCount(), masked.hitTerms(), masked.riskHints()))
                .orElse(masked);
    }
}
