package com.example.worklog.abstraction;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 日次メモを自社報告向けの抽象度に書き換える実装。
 *
 * <p>HTTP や疎通確認は {@link LlmClient} が担う。ここが持つのはプロンプトだけ。
 */
@Service
public class LlmAbstractionService implements AbstractionService {

    private static final String SYSTEM_PROMPT = """
            あなたはSES技術者の業務メモを、自社への報告用に抽象化するアシスタントです。

            厳守事項:
            1. 入力に書かれていない事実を絶対に追加しない。推測や補完をしない。
            2. 固有名詞（会社名・システム名・製品名・人名・部署名）が残っていれば一般名詞に言い換える。
               例:「決済API」→「外部連携機能」、「田中さん」→「チームメンバー」
            3. 実施した作業・判明した事実・次の予定のみを、事実ベースで記述する。
            4. 全体で2〜4文、200字以内。
            5. 出力は本文のみ。前置き・見出し・箇条書き記号・補足説明を一切付けない。
            6. 業務内容として解釈できない場合は、入力をそのまま返す。
            """;

    private final LlmClient client;

    public LlmAbstractionService(LlmClient client) {
        this.client = client;
    }

    @Override
    public LlmStatus status() {
        return client.status();
    }

    @Override
    public Optional<String> abstractText(String maskedText) {
        if (maskedText == null || maskedText.isBlank()) {
            return Optional.empty();
        }
        return client.complete(SYSTEM_PROMPT,
                "以下の業務メモを抽象化してください。\n\n---\n" + maskedText + "\n---");
    }
}
