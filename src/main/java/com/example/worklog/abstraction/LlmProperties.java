package com.example.worklog.abstraction;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ローカルLLM の接続設定。
 * OpenAI 互換エンドポイントであれば Ollama / vLLM / LM Studio のいずれでも動く。
 */
@ConfigurationProperties(prefix = "worklog.llm")
public record LlmProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int timeoutSeconds,
        String reasoningEffort) {

    public LlmProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434/v1";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "local";
        }
        if (model == null || model.isBlank()) {
            model = "qwen3-flash";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 120;
        }
        // 空文字は「送らない」を意味する（reasoning_effort に対応しないサーバ向け）
        if (reasoningEffort != null && reasoningEffort.isBlank()) {
            reasoningEffort = null;
        }
    }
}
