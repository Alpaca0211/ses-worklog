package com.example.worklog.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

    private final String base64Key;

    public CryptoConfig(@Value("${worklog.crypto.key:}") String base64Key) {
        this.base64Key = base64Key;
    }

    @PostConstruct
    void init() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("""

                    ------------------------------------------------------------
                    暗号鍵が設定されていません。
                    生ログは客先の機密を含みうるため、鍵なしでの起動は許可していません。

                    src/main/resources/application-local.yml に以下を記述してください:

                      worklog:
                        crypto:
                          key: <Base64 エンコードした 32 バイトのランダム値>

                    鍵は次のコマンドで生成できます:
                      java -e ... もしくは scripts/gen-key.ps1 を実行
                    ------------------------------------------------------------
                    """);
        }
        CryptoKeyHolder.initialize(base64Key);
    }
}
