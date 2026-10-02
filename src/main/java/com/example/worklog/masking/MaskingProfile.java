package com.example.worklog.masking;

import com.example.worklog.domain.TermCategory;
import java.util.EnumSet;
import java.util.Set;

/**
 * 出力先ごとのマスキング強度。
 *
 * <p>過去 4 年分の週報を調べたところ、自社の週報システムには案件名もコンポーネント名も
 * そのまま書かれている一方、人名は出ていなかった。案件名まで伏せると
 * 「どの案件の話か」が失われて週報として機能しないため、宛先で強度を変える。
 */
public enum MaskingProfile {

    /** 自社の週報システム向け。案件・システム名は残し、人名などのみ伏せる。 */
    INTERNAL("社内週報向け", EnumSet.of(TermCategory.PERSON, TermCategory.OTHER)),

    /** 社外（職務経歴書・ポートフォリオ）向け。全分類を伏せる。 */
    EXTERNAL("社外向け", EnumSet.allOf(TermCategory.class));

    private final String label;
    private final Set<TermCategory> categories;

    MaskingProfile(String label, Set<TermCategory> categories) {
        this.label = label;
        this.categories = categories;
    }

    public String getLabel() {
        return label;
    }

    public boolean masks(TermCategory category) {
        return categories.contains(category);
    }
}
