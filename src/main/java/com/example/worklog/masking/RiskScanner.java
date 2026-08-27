package com.example.worklog.masking;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.example.worklog.domain.TermCategory;
import org.springframework.stereotype.Service;

/**
 * 辞書に未登録だが、固有名詞・機密の可能性がある語を検出する補助スキャナ。
 *
 * <p>これはブロックしない。あくまで「辞書に登録し忘れていませんか」と入力時に促すためのもの。
 * 秘匿化の主役はあくまで {@link MaskingService} の決定論的な辞書マッチングであり、
 * ここでの検出漏れがあっても設計上の安全性は辞書側で担保される。
 */
@Service
public class RiskScanner {

    public record RiskHint(String type, String value) {

        /** 検出種別から辞書登録時の分類を推定する（クイック追加の初期値）。 */
        public TermCategory suggestedCategory() {
            return switch (type) {
                case TYPE_COMPANY -> TermCategory.CLIENT;
                case TYPE_PERSON -> TermCategory.PERSON;
                case TYPE_CODE_NAME -> TermCategory.SYSTEM;
                default -> TermCategory.OTHER;
            };
        }
    }

    static final String TYPE_EMAIL = "メールアドレス";
    static final String TYPE_URL = "URL";
    static final String TYPE_IP = "IPアドレス";
    static final String TYPE_COMPANY = "会社名の可能性";
    static final String TYPE_NUMBER = "識別番号の可能性";
    static final String TYPE_PERSON = "人名の可能性";
    static final String TYPE_CODE_NAME = "システム・製品名の可能性";

    // 注意: Java の \b は Character.isLetterOrDigit で境界を判定するため、漢字も単語構成文字とみなされる。
    // 「伝票番号20260827001」のように日本語に数字や英字が直結すると \b が成立せず検出漏れになる。
    // そのため単語境界は使わず、前後の文字種を否定先読み/後読みで直接指定している。
    private static final Pattern EMAIL =
            Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern URL =
            Pattern.compile("https?://[\\w./?%&=#:+-]+");
    private static final Pattern IPV4 =
            Pattern.compile("(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)");
    private static final Pattern COMPANY =
            Pattern.compile("(?:株式会社|有限会社|合同会社)\\s*[\\p{IsHan}\\p{IsKatakana}\\p{IsHiragana}A-Za-z0-9]{1,20}"
                    + "|[\\p{IsHan}\\p{IsKatakana}A-Za-z0-9]{1,20}\\s*(?:株式会社|有限会社|合同会社)");
    private static final Pattern LONG_NUMBER =
            Pattern.compile("(?<!\\d)\\d{8,}(?!\\d)");
    private static final Pattern PERSON =
            Pattern.compile("([\\p{IsHan}\\p{IsKatakana}A-Za-z]{2,8})(?=さん|氏|部長|課長|係長|主任|リーダー|マネージャ)");
    private static final Pattern CODE_NAME =
            Pattern.compile("(?<![A-Za-z0-9])[A-Z][A-Za-z0-9]{2,}(?:[_-][A-Za-z0-9]+)*(?![A-Za-z0-9])");

    /** CODE_NAME の誤検出を減らすための一般技術用語。必要に応じて育てる。 */
    private static final Set<String> TECH_STOPWORDS = Set.of(
            "API", "SQL", "AWS", "GCP", "HTTP", "HTTPS", "REST", "JSON", "XML", "CSV",
            "JVM", "JDK", "IDE", "SES", "NDA", "PoC",
            "Java", "Spring", "Boot", "Docker", "Kubernetes", "Linux", "Windows", "Git", "GitHub",
            "React", "Vue", "Angular", "TypeScript", "JavaScript", "Python", "PostgreSQL", "MySQL",
            "Oracle", "Redis", "Kafka", "Nginx", "Apache", "Maven", "Gradle", "JUnit", "Mockito",
            "Excel", "Word", "Teams", "Slack", "Jira", "Redmine", "Backlog", "Confluence");

    public List<RiskHint> scan(String text) {
        Set<RiskHint> hints = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        collect(hints, EMAIL, text, TYPE_EMAIL, 0);
        collect(hints, URL, text, TYPE_URL, 0);
        collect(hints, IPV4, text, TYPE_IP, 0);
        collect(hints, COMPANY, text, TYPE_COMPANY, 0);
        collect(hints, LONG_NUMBER, text, TYPE_NUMBER, 0);
        collect(hints, PERSON, text, TYPE_PERSON, 1);

        Matcher m = CODE_NAME.matcher(text);
        while (m.find()) {
            String v = m.group();
            if (!TECH_STOPWORDS.contains(v)) {
                hints.add(new RiskHint(TYPE_CODE_NAME, v));
            }
        }
        return List.copyOf(hints);
    }

    private void collect(Set<RiskHint> out, Pattern pattern, String text, String type, int group) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String v = m.group(group);
            if (v != null && !v.isBlank()) {
                out.add(new RiskHint(type, v.trim()));
            }
        }
    }
}
