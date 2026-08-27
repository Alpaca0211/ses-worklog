package com.example.worklog.crypto;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * JPA の AttributeConverter は Bean ライフサイクルの外で生成されうるため、
 * 鍵は起動時に静的に注入する（{@link CryptoConfig} が設定する）。
 */
public final class CryptoKeyHolder {

    private static volatile SecretKeySpec key;

    private CryptoKeyHolder() {
    }

    static void initialize(String base64Key) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("worklog.crypto.key が Base64 として不正です。", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "worklog.crypto.key は 32 バイト（AES-256）である必要があります。実際: " + decoded.length + " バイト");
        }
        key = new SecretKeySpec(decoded, "AES");
    }

    static SecretKeySpec key() {
        SecretKeySpec k = key;
        if (k == null) {
            throw new IllegalStateException("暗号鍵が未初期化です。worklog.crypto.key を設定してください。");
        }
        return k;
    }
}
