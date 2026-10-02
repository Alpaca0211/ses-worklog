package com.example.worklog.domain;

/** スキルシートの技能歴の区分。シートの 2 列構成に対応する。 */
public enum TechnologyCategory {

    LANGUAGE_FRAMEWORK("言語・フレームワーク"),
    OS_TOOL("OS・その他（ツールなど）");

    private final String label;

    TechnologyCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
