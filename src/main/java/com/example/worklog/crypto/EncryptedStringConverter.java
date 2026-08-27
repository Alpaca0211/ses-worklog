package com.example.worklog.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 生ログ列の保存時暗号化（AES-256-GCM）。
 *
 * <p>保存形式: {@code enc:v1:} + Base64(IV(12byte) || ciphertext+tag)
 * 接頭辞を付けることで、平文が混入した場合に検出できる。
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, CryptoKeyHolder.key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("生ログの暗号化に失敗しました。", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (!dbData.startsWith(PREFIX)) {
            // 平文で保存されている＝暗号化前のデータ。読めるが警告対象。
            return dbData;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, CryptoKeyHolder.key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("生ログの復号に失敗しました。鍵が変更された可能性があります。", e);
        }
    }
}
