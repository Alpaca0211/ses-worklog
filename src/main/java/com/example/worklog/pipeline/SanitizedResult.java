package com.example.worklog.pipeline;

import com.example.worklog.domain.SanitizeStatus;
import com.example.worklog.masking.RiskScanner.RiskHint;
import java.util.List;
import java.util.Map;

/**
 * @param maskedText  辞書マスキング直後（LLM への入力）
 * @param outputText  最終出力。これ以外を外部に出してはならない。
 * @param status      どこまで処理できたか
 * @param maskedCount 辞書で置換した件数
 * @param hitTerms    ヒットした用語 → 回数
 * @param riskHints   辞書未登録だが固有名詞の可能性がある語
 */
public record SanitizedResult(
        String maskedText,
        String outputText,
        SanitizeStatus status,
        int maskedCount,
        Map<String, Integer> hitTerms,
        List<RiskHint> riskHints) {
}
