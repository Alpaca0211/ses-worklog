package com.example.worklog.career;

import com.example.worklog.domain.TechnologyCategory;

/**
 * 技能歴 1 行分。経験年数は、その技術を使った案件の期間を合計して求める。
 *
 * <p>手で積み上げると案件が増えるたびに全技術を数え直すことになり、更新漏れも起きる。
 * 集計で出せば値は常に記録と一致する。
 */
public record TechnologyExperience(String name, TechnologyCategory category, int months) {

    public String duration() {
        return Durations.format(months);
    }
}
