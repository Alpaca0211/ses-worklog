package com.example.worklog.abstraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 設定モデル名と Ollama 取得済みモデルの照合。
 *
 * <p>ここを取り違えると「LLM 接続中」と表示しながら補完が毎回失敗するため、
 * タグ省略時の挙動を明示的に固定しておく。
 */
class ModelMatchingTest {

    private LlmClient clientFor(String model) {
        return new LlmClient(
                new LlmProperties(true, "http://localhost:11434/v1", "local", model, 0.2, 120, "none"));
    }

    @Test
    void タグまで一致すれば取得済みと判定する() {
        assertThat(clientFor("qwen3.5:9b").hasConfiguredModel(List.of("qwen3.5:9b"))).isTrue();
    }

    @Test
    void タグ省略時は同名の任意タグに一致する() {
        assertThat(clientFor("qwen3.5").hasConfiguredModel(List.of("qwen3.5:9b"))).isTrue();
    }

    @Test
    void タグを明示した場合は別タグに一致しない() {
        // "qwen3.5:9b" を要求しているのに 4b しか無い状態を取得済みと誤認しないこと
        assertThat(clientFor("qwen3.5:9b").hasConfiguredModel(List.of("qwen3.5:4b"))).isFalse();
    }

    @Test
    void 前方一致するだけの別モデルには一致しない() {
        // "qwen3" が "qwen3.5:9b" に一致してしまうと誤ったモデルで動く
        assertThat(clientFor("qwen3").hasConfiguredModel(List.of("qwen3.5:9b"))).isFalse();
    }

    @Test
    void モデルが一つも無ければ取得済みではない() {
        assertThat(clientFor("qwen3.5:9b").hasConfiguredModel(List.of())).isFalse();
    }
}
