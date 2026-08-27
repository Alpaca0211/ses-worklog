package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.masking.RiskScanner;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 辞書未登録の固有名詞候補を拾えるかの検証。Spring コンテキストは不要。 */
class RiskScannerTest {

    private final RiskScanner scanner = new RiskScanner();

    private List<String> values(String text) {
        return scanner.scan(text).stream().map(RiskScanner.RiskHint::value).toList();
    }

    @Test
    void 日本語に直結した数字列を検出する() {
        // Java の \b は漢字を単語構成文字とみなすため、単語境界では検出できないケース
        assertThat(values("伝票番号20260827001の確認")).contains("20260827001");
    }

    @Test
    void 日本語に直結した英字トークンを検出する() {
        assertThat(values("来週OrionWMS側の修正PRを出す")).contains("OrionWMS");
    }

    @Test
    void 日本語に直結したIPアドレスを検出する() {
        assertThat(values("接続先は192.168.0.10です")).contains("192.168.0.10");
    }

    @Test
    void 一般的な技術用語は固有名詞として扱わない() {
        assertThat(values("JavaとSpringで実装しPostgreSQLに保存した"))
                .doesNotContain("Java", "Spring", "PostgreSQL");
    }

    @Test
    void 会社名と人名とメールアドレスを検出する() {
        List<String> v = values("株式会社テスト光学の山田さんへ連絡（yamada@example.co.jp）");

        assertThat(v).contains("山田", "yamada@example.co.jp");
        assertThat(v).anyMatch(s -> s.contains("テスト光学"));
    }

    @Test
    void 業務に無関係な短い文では何も検出しない() {
        assertThat(values("本日は定例会議に参加した")).isEmpty();
    }
}
