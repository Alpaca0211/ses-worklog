package com.example.worklog.career;

import java.util.List;

/**
 * 職務経歴の下書き 1 期間分。
 *
 * <p>案件別の実績と、期間全体の取り組みを分けて持つ。
 * 日次メモは案件に紐付けていない（紐付けを求めると入力の手間が増え、
 * 記録が続かなくなる）ため、取り組みを案件へ割り当てることはできない。
 * 特定の案件に属するかのように見せるより、期間全体のものとして示すほうが正確。
 */
public record CareerDraft(List<CareerEntry> entries, List<String> achievements) {

    public boolean isEmpty() {
        return entries == null || entries.isEmpty();
    }

    public boolean hasAchievements() {
        return achievements != null && !achievements.isEmpty();
    }
}
