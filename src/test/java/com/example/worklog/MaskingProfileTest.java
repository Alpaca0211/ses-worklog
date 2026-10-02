package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.domain.TermCategory;
import com.example.worklog.masking.MaskingProfile;
import com.example.worklog.masking.MaskingService;
import com.example.worklog.masking.TermService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 宛先別のマスキング強度。
 *
 * <p>自社の週報システムには案件名もコンポーネント名も書かれているため、
 * そこまで伏せると「どの案件の話か」が失われて週報として機能しない。
 * 一方で社外に出る職務経歴では全て伏せる必要がある。
 */
class MaskingProfileTest extends SanitizeTestBase {

    @Autowired
    MaskingService maskingService;

    @Autowired
    TermService termService;

    @BeforeEach
    void setUp() {
        termService.add("アルタイル物流", "顧客企業", TermCategory.CLIENT);
        termService.add("AltairWMS", "担当システム", TermCategory.SYSTEM);
        termService.add("次期刷新PJ", "担当案件", TermCategory.PROJECT);
        termService.add("小田切", "チームメンバー", TermCategory.PERSON);
    }

    private static final String INPUT =
            "アルタイル物流のAltairWMSについて、次期刷新PJの一環で小田切と対応した";

    @Test
    void 社内週報向けは案件名とシステム名を残し人名だけ伏せる() {
        String text = maskingService.mask(INPUT, MaskingProfile.INTERNAL).text();

        assertThat(text).contains("アルタイル物流", "AltairWMS", "次期刷新PJ");
        assertThat(text).doesNotContain("小田切").contains("チームメンバー");
    }

    @Test
    void 社外向けは全て伏せる() {
        String text = maskingService.mask(INPUT, MaskingProfile.EXTERNAL).text();

        assertThat(text)
                .doesNotContain("アルタイル物流", "AltairWMS", "次期刷新PJ", "小田切")
                .contains("顧客企業", "担当システム", "担当案件", "チームメンバー");
    }

    @Test
    void 既定は社外向けで安全側に倒れる() {
        assertThat(maskingService.mask(INPUT).text())
                .isEqualTo(maskingService.mask(INPUT, MaskingProfile.EXTERNAL).text());
    }

    @Test
    void 残存検査もプロファイルに従う() {
        String internal = maskingService.mask(INPUT, MaskingProfile.INTERNAL).text();

        // 社内向けの出力に案件名が残っていても、社内向けとしては違反ではない
        assertThat(maskingService.detectLeaks(internal, MaskingProfile.INTERNAL)).isEmpty();
        // 同じ文字列でも社外向けの基準では違反として検出される
        assertThat(maskingService.detectLeaks(internal, MaskingProfile.EXTERNAL))
                .contains("アルタイル物流", "AltairWMS");
    }
}
