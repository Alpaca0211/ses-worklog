package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.domain.TermCategory;
import com.example.worklog.masking.MaskingResult;
import com.example.worklog.masking.MaskingService;
import com.example.worklog.masking.TermService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MaskingServiceTest extends SanitizeTestBase {

    @Autowired
    MaskingService maskingService;

    @Autowired
    TermService termService;

    @BeforeEach
    void setUp() {
        termService.add("テスト電機", "顧客企業", TermCategory.CLIENT);
        termService.add("テスト電機株式会社", "顧客企業", TermCategory.CLIENT);
        termService.add("ZenithPay", "外部連携機能", TermCategory.SYSTEM);
        termService.add("佐々木", "チームメンバー", TermCategory.PERSON);
    }

    @Test
    void 登録語を置換して件数を返す() {
        MaskingResult r = maskingService.mask("テスト電機のZenithPayを調査。佐々木に共有。");

        assertThat(r.text()).isEqualTo("顧客企業の外部連携機能を調査。チームメンバーに共有。");
        assertThat(r.totalCount()).isEqualTo(3);
    }

    @Test
    void 語境界のない日本語でも部分一致で置換される() {
        // 「テスト電機の」のように助詞が続いても検出できることが要件
        assertThat(maskingService.mask("本日テスト電機へ訪問").text()).isEqualTo("本日顧客企業へ訪問");
    }

    @Test
    void 競合する語は最長一致が優先される() {
        // 「テスト電機」と「テスト電機株式会社」が競合しても、より具体的な方が選ばれ
        // 「顧客企業株式会社」のような壊れた出力にならないこと
        assertThat(maskingService.mask("テスト電機株式会社と契約").text()).isEqualTo("顧客企業と契約");
    }

    @Test
    void 英数字の用語は大文字小文字を無視して置換される() {
        assertThat(maskingService.mask("zenithpay の障害").text()).isEqualTo("外部連携機能 の障害");
    }

    @Test
    void 未登録の語は置換されない() {
        MaskingResult r = maskingService.mask("一般的な単体テストを実施した");

        assertThat(r.isClean()).isTrue();
        assertThat(r.text()).isEqualTo("一般的な単体テストを実施した");
    }

    @Test
    void 残存検査は禁止用語を検出する() {
        assertThat(maskingService.detectLeaks("テスト電機の件")).containsExactly("テスト電機");
        assertThat(maskingService.detectLeaks("顧客企業の件")).isEmpty();
    }
}
