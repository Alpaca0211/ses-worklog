package com.example.worklog.abstraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenAI 互換エンドポイント経由でローカルLLM を呼ぶ共通クライアント。
 *
 * <p>Spring AI を使わず素の RestClient にしているのは、必要なのが
 * 「1 回の非ストリーミング補完」だけであり、依存とバージョン制約を増やす利点が無いため。
 * RAG やベクトルストアが必要になる段階で載せ替える。
 *
 * <p>用途ごとのプロンプトは呼び出し側が持つ。ここは HTTP・疎通確認・
 * 思考モード抑制といった、どの用途でも共通の関心事だけを扱う。
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    /** 一部のモデルが本文中に埋め込む思考ブロックを除去する。 */
    private static final Pattern THINK_BLOCK =
            Pattern.compile("<think>.*?</think>", Pattern.DOTALL);

    private final LlmProperties properties;
    private final RestClient client;

    private volatile Instant lastHealthCheck = Instant.EPOCH;
    private volatile LlmStatus status = LlmStatus.UNREACHABLE;

    public LlmClient(LlmProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    public LlmStatus status() {
        if (!properties.enabled()) {
            return LlmStatus.DISABLED;
        }
        // 毎リクエストで疎通確認すると遅いので 60 秒キャッシュする
        if (Duration.between(lastHealthCheck, Instant.now()).getSeconds() < 60) {
            return status;
        }
        lastHealthCheck = Instant.now();
        status = probe();
        return status;
    }

    public boolean isAvailable() {
        return status().isUsable();
    }

    /** 補完を 1 回実行する。失敗時は empty を返し、呼び出し側は縮退運転に入る。 */
    public Optional<String> complete(String systemPrompt, String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank() || !isAvailable()) {
            return Optional.empty();
        }
        ChatRequest request = new ChatRequest(
                properties.model(),
                List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                properties.temperature(),
                false,
                properties.reasoningEffort());
        try {
            ChatResponse response = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);

            String content = extractContent(response);
            return content.isBlank() ? Optional.empty() : Optional.of(content);
        } catch (Exception e) {
            log.warn("LLM 呼び出しに失敗しました: {}", e.toString());
            status = LlmStatus.UNREACHABLE;
            lastHealthCheck = Instant.EPOCH; // 次回アクセス時に必ず再確認する
            return Optional.empty();
        }
    }

    /**
     * サーバへの到達性だけでなく、設定モデルが実際に取得済みかまで確認する。
     * Ollama はモデル未取得でも {@code /v1/models} に 200 を返すため、
     * 到達性だけを見ると「接続中」と表示しながら補完が毎回失敗することになる。
     */
    private LlmStatus probe() {
        List<String> ids;
        try {
            ModelList list = client.get().uri("/models").retrieve().body(ModelList.class);
            ids = (list == null || list.data() == null)
                    ? List.of()
                    : list.data().stream().map(ModelInfo::id).filter(Objects::nonNull).toList();
        } catch (Exception e) {
            logOnChange(LlmStatus.UNREACHABLE, () ->
                    log.warn("ローカルLLM に到達できません。辞書マスキングのみで縮退運転します: {}", e.getMessage()));
            return LlmStatus.UNREACHABLE;
        }
        if (!hasConfiguredModel(ids)) {
            logOnChange(LlmStatus.MODEL_MISSING, () -> log.warn(
                    "ローカルLLM は起動していますが、設定モデル '{}' が取得されていません。"
                            + "`ollama pull {}` を実行するか worklog.llm.model を修正してください。取得済み: {}",
                    properties.model(), properties.model(), ids));
            return LlmStatus.MODEL_MISSING;
        }
        logOnChange(LlmStatus.READY, () ->
                log.info("ローカルLLM に接続しました: {} (model={})", properties.baseUrl(), properties.model()));
        return LlmStatus.READY;
    }

    /** タグを省略した設定（例: "qwen3.5"）は同名の任意タグに一致させる。 */
    boolean hasConfiguredModel(List<String> ids) {
        String model = properties.model();
        if (ids.contains(model)) {
            return true;
        }
        if (model.contains(":")) {
            return false;
        }
        return ids.stream().anyMatch(id -> id.startsWith(model + ":"));
    }

    /** 状態が変化したときだけ記録する。60 秒ごとに同じ警告を出してログを埋めないため。 */
    private void logOnChange(LlmStatus next, Runnable logger) {
        if (status != next) {
            logger.run();
        }
    }

    private String extractContent(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return "";
        }
        Choice choice = response.choices().get(0);
        Message message = choice.message();
        String raw = (message == null || message.content() == null) ? "" : message.content();
        String content = THINK_BLOCK.matcher(raw).replaceAll("").trim();

        if (content.isBlank()) {
            // 思考型モデルの典型的な失敗。思考だけで文脈上限に達し、本文が生成されていない。
            // reasoning は content とは別フィールドで返るため、<think> の除去では防げない。
            log.warn("LLM が本文を返しませんでした（finish_reason={}）。"
                            + "思考モデルの場合は worklog.llm.reasoning-effort=none を設定してください。",
                    choice.finishReason());
        }
        return content;
    }

    /**
     * {@code reasoning_effort} は思考モードの抑制に使う。
     * 思考型モデルは既定で数千トークンを思考に費やし、本文が出る前に文脈上限に達しうる
     * （実測: 未指定 90 秒・本文空、"none" 指定 1 秒・本文あり）。
     * 対応しないサーバ向けに null を許容し、その場合はフィールドごと送らない。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChatRequest(String model, List<Message> messages, double temperature, boolean stream,
                       @JsonProperty("reasoning_effort") String reasoningEffort) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelInfo(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelList(List<ModelInfo> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {
    }
}
