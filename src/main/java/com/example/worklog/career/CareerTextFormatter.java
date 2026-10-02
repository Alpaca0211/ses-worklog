package com.example.worklog.career;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * スキルシートへ貼り付けられるテキストへ組み立てる。
 * 構造化データからの文字列生成であり、LLM は使わない。
 *
 * <p>体裁は現在運用しているスキルシート（技能経歴書）の項目に合わせてある。
 * 業務実績は案件ごとに ≪案件名≫・[概要]・[担当業務]・[チーム構成]・[規模] の順。
 */
@Service
public class CareerTextFormatter {

    public String format(CareerDraft draft) {
        if (draft == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendTechnologies(sb, draft);
        appendEntries(sb, draft);
        appendAchievements(sb, draft);
        return sb.toString().stripTrailing();
    }

    /** 技能歴。経験年数は案件期間の合計なので、案件を登録するだけで最新になる。 */
    private void appendTechnologies(StringBuilder sb, CareerDraft draft) {
        if (!draft.hasTechnologyExperience()) {
            return;
        }
        sb.append("■ 技能歴\n");
        appendTechnologyBlock(sb, "【言語・フレームワーク】", draft.languages());
        appendTechnologyBlock(sb, "【OS・その他（ツールなど）】", draft.tools());
        sb.append('\n');
    }

    private void appendTechnologyBlock(StringBuilder sb, String heading, List<TechnologyExperience> items) {
        if (items.isEmpty()) {
            return;
        }
        sb.append(heading).append('\n');
        for (TechnologyExperience t : items) {
            sb.append(t.name()).append('\t').append(t.duration()).append('\n');
        }
    }

    private void appendEntries(StringBuilder sb, CareerDraft draft) {
        if (draft.isEmpty()) {
            return;
        }
        sb.append("■ 業務実績\n");
        int no = 1;
        for (CareerEntry e : draft.entries()) {
            if (no > 1) {
                sb.append('\n');
            }
            sb.append(no++).append('.');
            if (e.has(e.industry())) {
                sb.append(' ').append(e.industry());
            }
            sb.append('\n');
            sb.append("≪").append(e.publicLabel()).append("≫\n");

            appendSection(sb, "[概要]", e.overview());
            appendSection(sb, "[担当業務]", e.taskSummary());
            appendSection(sb, "[チーム構成]", e.teamComposition());
            appendSection(sb, "[規模]", e.projectScale());

            sb.append("期間: ").append(e.periodRange()).append('\n');
            sb.append("稼動月数: ").append(e.duration()).append('\n');
            if (e.has(e.environment())) {
                sb.append("環境: ").append(e.environment()).append('\n');
            }
            if (!e.technologies().isEmpty()) {
                sb.append("使用技術: ").append(e.technologySummary()).append('\n');
            }
        }
    }

    private void appendSection(StringBuilder sb, String heading, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        sb.append(heading).append('\n').append(stripLeadingHeading(heading, body)).append('\n');
    }

    /**
     * 本文の先頭に見出しがそのまま入っている場合は取り除く。
     * 既存のシートから貼り付けると「[概要]」ごと入ってきて、見出しが二重になるため。
     */
    private String stripLeadingHeading(String heading, String body) {
        String stripped = body.strip();
        return stripped.startsWith(heading)
                ? stripped.substring(heading.length()).strip()
                : stripped;
    }

    /**
     * 取り組みは案件別ではなく期間全体として示す。
     * 日次メモを案件に紐付けていない以上、特定の案件のものとは言えないため。
     */
    private void appendAchievements(StringBuilder sb, CareerDraft draft) {
        if (!draft.hasAchievements()) {
            return;
        }
        sb.append("\n■ 取り組み・実績\n");
        for (String a : draft.achievements()) {
            sb.append('・').append(a).append('\n');
        }
    }
}
