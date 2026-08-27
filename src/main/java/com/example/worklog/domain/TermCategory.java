package com.example.worklog.domain;

/** 禁止用語の分類。UI の絞り込みと、置換先のデフォルト決定に使う。 */
public enum TermCategory {
    CLIENT("顧客・取引先", "顧客企業"),
    SYSTEM("システム・製品名", "担当システム"),
    PERSON("人名", "関係者"),
    PROJECT("案件・プロジェクト名", "担当案件"),
    ORG("部署・チーム名", "関係部署"),
    OTHER("その他", "（マスク）");

    private final String label;
    private final String defaultReplacement;

    TermCategory(String label, String defaultReplacement) {
        this.label = label;
        this.defaultReplacement = defaultReplacement;
    }

    public String getLabel() {
        return label;
    }

    public String getDefaultReplacement() {
        return defaultReplacement;
    }
}
