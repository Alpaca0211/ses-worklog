package com.example.worklog.domain;

/** 秘匿化パイプラインの処理結果。 */
public enum SanitizeStatus {
    /** 辞書マスキングのみ。LLM が無効 or 到達不能だった場合。 */
    MASKED_ONLY("辞書マスキングのみ"),
    /**
     * 短いため抽象化しない。
     * 情報量の少ない入力ほど LLM は内容を補完するため、意図的に通さない。
     */
    ABSTRACTION_SKIPPED("抽象化不要（短文）"),
    /** 辞書マスキング後、LLM による抽象化まで完了。 */
    ABSTRACTED("抽象化済み"),
    /** LLM 出力に辞書用語が再出現したため、出力側で再マスキングした。 */
    ABSTRACTED_REMASKED("抽象化＋再マスキング");

    private final String label;

    SanitizeStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
