package com.example.worklog.career;

import com.example.worklog.abstraction.LlmClient;
import com.example.worklog.masking.MaskingProfile;
import com.example.worklog.masking.MaskingService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 職務経歴の「取り組み・実績」を生成する。
 *
 * <p>材料は日次メモの非定型記述と、取り込んだ過去週報の記述。
 * 定型作業をこなした事実は件数の集計が担うため、ここでは扱わない。
 *
 * <p>社外に出る文章のため、入力・出力の両方に社外向けマスキングを掛ける。
 */
@Service
public class AchievementGenerator {

    private static final Logger log = LoggerFactory.getLogger(AchievementGenerator.class);

    private static final String NONE = "なし";

    private static final String SYSTEM_PROMPT = """
            あなたはSES技術者の職務経歴書の「取り組み・実績」欄を書くアシスタントです。

            入力は、その案件で書き残された業務メモです。
            そこから、経歴として意味のある取り組みだけを箇条書きにしてください。

            厳守事項:
            1. 入力に書かれていない事実・成果・数値・効果を絶対に追加しない。誇張しない。
            2. である調で書く。です・ます調は使わない。
            3. 1項目あたり40〜80字。全体で2〜4項目。
            4. 定型作業を実施した事実そのものは書かない。件数は別途集計で示すため重複する。
               改善、効率化、手順の整備、新規対応、調査と共有など、
               主体的な行動として評価される内容だけを書く。
            5. 固有名詞（会社名・製品名・システム名・人名）は書かない。
               入力に残っていても一般名詞に言い換える。
            6. 出力は1行1項目。行頭に記号や番号を付けない。
            7. 該当する取り組みが入力に無ければ、「なし」とだけ出力する。

            記述の粒度の例:
            リリース手順の属人化を解消するため、動作確認手順を文書化しチームへ展開した。
            定常作業の手順変更に伴い、追加・不要となった工程を洗い出して手順書を整備した。
            """;

    private final LlmClient client;
    private final MaskingService maskingService;

    public AchievementGenerator(LlmClient client, MaskingService maskingService) {
        this.client = client;
        this.maskingService = maskingService;
    }

    /**
     * @param materials 日次メモや過去週報の生テキスト。マスキングはこの中で行う
     * @return 箇条書きの各項目。生成できなければ空リスト
     */
    public List<String> generate(List<String> materials) {
        if (materials == null || materials.isEmpty() || !client.isAvailable()) {
            return List.of();
        }
        String notes = materials.stream()
                .filter(m -> m != null && !m.isBlank())
                // 社外向けの生成なので、入力の時点で固有名詞を落としておく
                .map(m -> maskingService.mask(m, MaskingProfile.EXTERNAL).text().trim())
                .distinct()
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        if (notes.isBlank()) {
            return List.of();
        }

        return client.complete(SYSTEM_PROMPT,
                        "以下はこの案件で書き残した業務メモです。経歴として書ける取り組みを抽出してください。\n\n"
                                + notes)
                .map(this::toBullets)
                .orElseGet(() -> {
                    log.info("実績の生成ができませんでした。件数の集計のみで出力します。");
                    return List.of();
                });
    }

    private List<String> toBullets(String generated) {
        String text = generated.trim();
        if (text.isBlank() || text.equals(NONE) || text.startsWith(NONE + "。")) {
            return List.of();
        }
        List<String> bullets = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String t = line.trim().replaceFirst("^[-・*\\d.)\\s]+", "").trim();
            if (t.isEmpty() || t.equals(NONE)) {
                continue;
            }
            // 生成後も最終ゲートとして必ず通す。社外に出る文章なので取りこぼしを許さない
            bullets.add(maskingService.mask(t, MaskingProfile.EXTERNAL).text());
        }
        return List.copyOf(bullets);
    }
}
