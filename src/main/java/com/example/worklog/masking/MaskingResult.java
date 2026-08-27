package com.example.worklog.masking;

import java.util.List;
import java.util.Map;

/**
 * @param text       置換後テキスト
 * @param hitCounts  ヒットした禁止用語 → 置換回数
 * @param totalCount 置換総数
 */
public record MaskingResult(String text, Map<String, Integer> hitCounts, int totalCount) {

    public boolean isClean() {
        return totalCount == 0;
    }

    public List<String> hitTerms() {
        return List.copyOf(hitCounts.keySet());
    }
}
