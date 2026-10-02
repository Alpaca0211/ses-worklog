package com.example.worklog.weekly;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * エクスポート CSV の読み取り。
 *
 * <p>テストデータは実運用の構造を模した架空のもの。CP932・二重エスケープ・
 * 引用符内の改行という、実データで確認された特徴を再現している。
 */
class WeeklyReportCsvParserTest {

    private final WeeklyReportCsvParser parser = new WeeklyReportCsvParser();

    /** 週報システムの出力を模した CSV。週ごとに見出し行と日付行が繰り返される入れ子構造。 */
    private static final String CSV = """
            "社員名"
            "架空 太郎"
            "年月週","直近で学んだこと、覚えたこと","コメント","リーダーからの返信","社長からの返信","その他の返信"
            "2026年8月4週","Aho-Corasick法を学んだ","&amp;gt;&amp;gt;話題
            返答の本文

            &amp;lt;プライベート&amp;gt;
            週末は旅行に行った

            【業務遂行】
            依頼された業務について、作業を滞りなく遂行することができている。
            リリース作業において、効率化のため手順書の整備を行った。

            【報連相】
            相談がある場合は画面共有で確認を行った。

            【人間関係】

            【お客様のお悩み】
            ","リーダーの返信本文","",""
            "日付","作業内容","","","",""
            "2026年8月17日","■やったこと
            ・作業内容","","","",""
            "2026年8月18日","","","","",""
            "年月週","直近で学んだこと、覚えたこと","コメント","リーダーからの返信","社長からの返信","その他の返信"
            "2026年8月3週","","","","",""
            "日付","作業内容","","","",""
            "2026年8月10日","","","","",""
            """;

    private List<WeeklyReportCsvParser.ParsedWeek> parse() {
        return parser.parse(CSV.getBytes(Charset.forName("windows-31j")));
    }

    @Test
    void 内容のある週だけを取り込む() {
        List<WeeklyReportCsvParser.ParsedWeek> weeks = parse();

        // 8月3週は全欄が空なので取り込まない
        assertThat(weeks).hasSize(1);
        assertThat(weeks.get(0).year()).isEqualTo(2026);
        assertThat(weeks.get(0).month()).isEqualTo(8);
        assertThat(weeks.get(0).weekNum()).isEqualTo(4);
    }

    @Test
    void 業務遂行の見出しの中身だけを取り出す() {
        var w = parse().get(0);

        assertThat(w.performance())
                .isEqualTo("""
                        依頼された業務について、作業を滞りなく遂行することができている。
                        リリース作業において、効率化のため手順書の整備を行った。""");
    }

    @Test
    void 報連相と学んだことも取り込む() {
        var w = parse().get(0);

        assertThat(w.communication()).isEqualTo("相談がある場合は画面共有で確認を行った。");
        assertThat(w.learned()).isEqualTo("Aho-Corasick法を学んだ");
    }

    @Test
    void 私生活の記述とリーダーの返信は取り込まない() {
        var w = parse().get(0);
        String all = w.performance() + w.communication() + w.learned();

        assertThat(all).doesNotContain("旅行", "プライベート", "リーダーの返信本文", "話題");
    }

    @Test
    void 空の見出しは空文字になる() {
        var w = parse().get(0);

        // 【人間関係】は取り込み対象外だが、【お客様のお悩み】の直前で切れていること
        assertThat(w.performance()).doesNotContain("【");
        assertThat(w.communication()).doesNotContain("【");
    }

    @Test
    void 二重エスケープされたHTMLエンティティを戻す() {
        // エクスポート側で &gt; がさらにエスケープされ &amp;gt; になっている
        assertThat(WeeklyReportCsvParser.unescapeTwice("&amp;gt;&amp;gt;話題")).isEqualTo(">>話題");
        assertThat(WeeklyReportCsvParser.unescapeTwice("&amp;lt;プライベート&amp;gt;"))
                .isEqualTo("<プライベート>");
    }

    @Test
    void 引用符内の改行を含むフィールドを1つの値として読む() {
        var rows = new WeeklyReportCsvParser.CsvReader("\"a\nb\",\"c\"\n\"d\",\"e\"").readAll();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("a\nb", "c");
        assertThat(rows.get(1)).containsExactly("d", "e");
    }

    @Test
    void 二重引用符のエスケープを読む() {
        var rows = new WeeklyReportCsvParser.CsvReader("\"say \"\"hi\"\"\",\"x\"").readAll();

        assertThat(rows.get(0)).containsExactly("say \"hi\"", "x");
    }

    @Test
    void 空のCSVでも落ちない() {
        assertThat(parser.parse(new byte[0])).isEmpty();
    }
}
