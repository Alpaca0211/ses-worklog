package com.example.worklog.abstraction;

/**
 * ローカルLLM の利用可否。
 *
 * <p>「到達できない」と「モデルが無い」を区別する。両者は原因も対処も違うのに、
 * まとめて「未接続」と表示すると原因究明ができなくなるため。
 */
public enum LlmStatus {

    READY("LLM 接続中", true),
    MODEL_MISSING("LLM 未設定（モデル未取得）", false),
    UNREACHABLE("LLM 未接続（辞書マスキングのみ）", false),
    DISABLED("LLM 無効（辞書マスキングのみ）", false);

    private final String label;
    private final boolean usable;

    LlmStatus(String label, boolean usable) {
        this.label = label;
        this.usable = usable;
    }

    public String getLabel() {
        return label;
    }

    public boolean isUsable() {
        return usable;
    }
}
