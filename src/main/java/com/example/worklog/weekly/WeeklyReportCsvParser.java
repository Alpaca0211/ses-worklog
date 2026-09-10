package com.example.worklog.weekly;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 週報システムのエクスポート CSV を読み取る。
 *
 * <p>この CSV は単純な表ではなく、週ごとに次の構造が繰り返される入れ子形式:
 * <pre>
 * "年月週","直近で学んだこと、覚えたこと","コメント",...
 * "2026年8月4週","...","...",...
 * "日付","作業内容",...
 * "2026年8月17日","...",...   ← 7 日分
 * </pre>
 *
 * <p>エクスポート側にいくつか既知の不備があるため、読み取り時に補正する。
 * <ul>
 *   <li>文字コードが CP932。指定できないため既定で CP932 として読む</li>
 *   <li>HTML エンティティが二重エスケープされている（{@code &amp;gt;} → {@code >}）</li>
 *   <li>CP932 に無い記号（▶ ▷ など）が {@code ?} に置き換わって出力される。
 *       これは情報が失われており復元できないため、そのまま扱う</li>
 * </ul>
 */
@Component
public class WeeklyReportCsvParser {

    /** 取り込む 1 週分。私生活の記述とリーダーの返信は取り込まない。 */
    public record ParsedWeek(int year, int month, int weekNum,
                             String performance, String communication, String learned) {

        public boolean hasContent() {
            return notBlank(performance) || notBlank(communication) || notBlank(learned);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    private static final Charset CP932 = Charset.forName("windows-31j");
    private static final Pattern WEEK_LABEL = Pattern.compile("^(\\d{4})年(\\d{1,2})月(\\d{1,2})週$");
    private static final Pattern SECTION = Pattern.compile("【([^】]+)】");

    public List<ParsedWeek> parse(byte[] csvBytes) {
        List<List<String>> rows = new CsvReader(new String(csvBytes, CP932)).readAll();
        List<ParsedWeek> weeks = new ArrayList<>();

        for (List<String> row : rows) {
            if (row.size() < 3) {
                continue;
            }
            Matcher m = WEEK_LABEL.matcher(row.get(0).trim());
            if (!m.matches()) {
                continue; // 見出し行・日付行・社員名行は読み飛ばす
            }
            Map<String, String> sections = splitSections(row.get(2));
            ParsedWeek week = new ParsedWeek(
                    Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)),
                    sections.getOrDefault("業務遂行", ""),
                    sections.getOrDefault("報連相", ""),
                    unescapeTwice(row.get(1)).trim());
            if (week.hasContent()) {
                weeks.add(week);
            }
        }
        return weeks;
    }

    /** コメント欄を【見出し】ごとに分割する。見出しの前にある私生活の記述は捨てる。 */
    private Map<String, String> splitSections(String comment) {
        Map<String, String> result = new LinkedHashMap<>();
        if (comment == null || comment.isBlank()) {
            return result;
        }
        String[] parts = SECTION.split(unescapeTwice(comment));
        Matcher m = SECTION.matcher(unescapeTwice(comment));
        int i = 1;
        while (m.find() && i < parts.length) {
            result.put(m.group(1), parts[i].trim());
            i++;
        }
        return result;
    }

    /**
     * 二重エスケープされた HTML エンティティを戻す。
     * {@code &amp;gt;} → {@code &gt;} → {@code >} の 2 段階。
     */
    static String unescapeTwice(String s) {
        return unescape(unescape(s == null ? "" : s));
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }

    /**
     * 最小限の RFC4180 リーダー。
     * 引用符内の改行を含むフィールドがあるため行単位の分割では読めない。
     * 依存を増やさずに済む規模なので自前で持つ。
     */
    static final class CsvReader {

        private final String text;
        private int pos;

        CsvReader(String text) {
            this.text = text;
        }

        List<List<String>> readAll() {
            List<List<String>> rows = new ArrayList<>();
            while (pos < text.length()) {
                List<String> row = readRow();
                if (!(row.size() == 1 && row.get(0).isEmpty())) {
                    rows.add(row);
                }
            }
            return rows;
        }

        private List<String> readRow() {
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;

            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (quoted) {
                    if (c == '"') {
                        if (pos < text.length() && text.charAt(pos) == '"') {
                            field.append('"');
                            pos++;
                        } else {
                            quoted = false;
                        }
                    } else {
                        field.append(c);
                    }
                } else if (c == '"') {
                    quoted = true;
                } else if (c == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                } else if (c == '\r') {
                    if (pos < text.length() && text.charAt(pos) == '\n') {
                        pos++;
                    }
                    break;
                } else if (c == '\n') {
                    break;
                } else {
                    field.append(c);
                }
            }
            row.add(field.toString());
            return row;
        }
    }
}
