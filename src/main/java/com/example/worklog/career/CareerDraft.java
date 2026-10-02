package com.example.worklog.career;

import com.example.worklog.domain.TechnologyCategory;
import java.util.List;

/**
 * スキルシートの下書き。
 *
 * <p>業務実績は指定した期間に絞るが、技能歴は全案件から集計する。
 * 経験年数は積み上げであり、表示期間で切ると値が変わってしまうため。
 */
public record CareerDraft(List<CareerEntry> entries,
                          List<String> achievements,
                          List<TechnologyExperience> technologyExperience) {

    public boolean isEmpty() {
        return entries == null || entries.isEmpty();
    }

    public boolean hasAchievements() {
        return achievements != null && !achievements.isEmpty();
    }

    public List<TechnologyExperience> languages() {
        return byCategory(TechnologyCategory.LANGUAGE_FRAMEWORK);
    }

    public List<TechnologyExperience> tools() {
        return byCategory(TechnologyCategory.OS_TOOL);
    }

    private List<TechnologyExperience> byCategory(TechnologyCategory category) {
        return technologyExperience == null ? List.of()
                : technologyExperience.stream().filter(t -> t.category() == category).toList();
    }

    public boolean hasTechnologyExperience() {
        return technologyExperience != null && !technologyExperience.isEmpty();
    }
}
