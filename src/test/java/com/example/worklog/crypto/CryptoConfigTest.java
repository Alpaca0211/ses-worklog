package com.example.worklog.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 鍵が無いときの起動拒否。
 *
 * <p>生ログを平文で保存させないための意図的な失敗なので、
 * 初めて動かす人が自力で復帰できるだけの案内が出ることまで確認する。
 */
class CryptoConfigTest {

    @Test
    void 鍵が未設定なら起動を拒否し生成手順を案内する() {
        assertThatThrownBy(() -> new CryptoConfig("").init())
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .contains("application-local.yml")
                        .contains("RandomNumberGenerator")
                        .contains("README.md"));
    }

    @Test
    void 鍵の長さが不足していれば起動を拒否する() {
        // Base64 として妥当でも 32 バイトでなければ AES-256 に使えない
        assertThatThrownBy(() -> new CryptoConfig("c2hvcnQ=").init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 バイト");
    }

    @Test
    void Base64として不正なら起動を拒否する() {
        assertThatThrownBy(() -> new CryptoConfig("これはBase64ではない").init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }
}
