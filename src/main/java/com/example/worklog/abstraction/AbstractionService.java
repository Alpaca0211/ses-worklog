package com.example.worklog.abstraction;

import java.util.Optional;

/**
 * マスキング済みテキストを、自社報告向けの抽象度に書き換える。
 *
 * <p>この処理は「あれば品質が上がる」ものであり、必須ではない。
 * LLM が停止していてもパイプライン全体は辞書マスキングだけで完結する（縮退運転）。
 */
public interface AbstractionService {

    /** 現在の利用可否。UI にそのまま表示できる粒度で理由を持つ。 */
    LlmStatus status();

    default boolean isAvailable() {
        return status().isUsable();
    }

    /** 抽象化に成功した場合のみ結果を返す。失敗時は empty（呼び出し側が縮退する）。 */
    Optional<String> abstractText(String maskedText);
}
