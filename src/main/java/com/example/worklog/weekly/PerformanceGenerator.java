package com.example.worklog.weekly;

import com.example.worklog.abstraction.LlmClient;
import com.example.worklog.domain.DailyLog;
import com.example.worklog.masking.MaskingProfile;
import com.example.worklog.masking.MaskingService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 【業務遂行】欄の 2 文目を生成する。
 *
 * <p>過去 4 年分の実績を調べたところ、この欄は 2 階建てだった。
 * 1 文目は「依頼された業務を滞りなく遂行できている」旨の定型文で、同一文が何週も続く。
 * 2 文目だけがその週固有の非定型の貢献であり、LLM が必要なのはここだけ。
 *
 * <p>入力は日次の自由記述メモ。定型作業の記録（WorkEntry）は使わない。
 * 定型作業をこなした事実は 1 文目と集計値が担うため、ここで重ねると冗長になる。
 */
@Service
public class PerformanceGenerator {

    private static final Logger log = LoggerFactory.getLogger(PerformanceGenerator.class);

    /** 該当する貢献が無いときにモデルが返す語。これを空扱いにする。 */
    private static final String NONE = "なし";

    private static final String SYSTEM_PROMPT = """
            あなたはSES技術者の週報の【業務遂行】欄の2文目を書くアシスタントです。
            この欄は人事評価に使われます。

            【業務遂行】は2階建てになっており、1文目は「依頼された業務を滞りなく遂行できている」
            旨の定型文です。あなたが書くのは2文目、すなわちその週にあった非定型の貢献だけです。

            厳守事項:
            1. 入力に書かれていない事実・成果・数値・効果を絶対に追加しない。誇張しない。
            2. である調で書く。です・ます調は使わない。
            3. 1文、40〜80字。長くても2文までとする。
            4. 定型作業をこなしたこと自体は書かない（1文目で述べているため）。
               手順書の整備、改善、効率化、新規対応、調査結果の共有など、
               非定型の行動だけを書く。
            5. 該当する行動が入力に無ければ、「なし」とだけ出力する。無理に埋めない。
            6. 出力は本文のみ。前置き・見出し・箇条書き記号を一切付けない。

            文体と粒度の例:
            リリース作業において、効率化のため、新たに必要となった手順、不要となった手順の整備を行った。
            脆弱性対応業務において、動作確認手順が変更となったため、効率化のため手順書の整備を行った。
            新規コンポーネントの初回リリース作業において、客先の担当者と連携し、リリース手順書の作成を実施した。
            """;

    private final LlmClient client;
    private final MaskingService maskingService;

    public PerformanceGenerator(LlmClient client, MaskingService maskingService) {
        this.client = client;
        this.maskingService = maskingService;
    }

    /**
     * 週内の日次メモから 2 文目を生成する。
     * 該当する貢献が無い場合・LLM が使えない場合はいずれも empty を返す。
     */
    public Optional<String> generateSecondSentence(List<DailyLog> weekLogs) {
        if (weekLogs == null || weekLogs.isEmpty()) {
            return Optional.empty();
        }
        String notes = buildNotes(weekLogs);
        if (notes.isBlank()) {
            return Optional.empty();
        }
        Optional<String> generated = client.complete(SYSTEM_PROMPT,
                "以下は今週の業務メモです。非定型の貢献があれば2文目を書いてください。\n\n" + notes);
        if (generated.isEmpty()) {
            return Optional.empty();
        }
        String text = generated.get().trim();
        if (text.equals(NONE) || text.startsWith(NONE + "。") || text.isBlank()) {
            log.info("該当する非定型の貢献が無いと判定されました。2文目は生成しません。");
            return Optional.empty();
        }
        // 社内週報向けでも人名は伏せる。最終ゲートとして必ず通す。
        String masked = maskingService.mask(text, MaskingProfile.INTERNAL).text();
        return Optional.of(masked);
    }

    /**
     * 日次メモを社内向けマスキングで整える。
     *
     * <p>案件名やコンポーネント名は残す。週報システムは自社の内部システムであり、
     * これらを伏せると「どの案件の話か」が失われて評価材料として成立しないため。
     * 人名などは伏せる。
     */
    private String buildNotes(List<DailyLog> weekLogs) {
        StringBuilder sb = new StringBuilder();
        for (DailyLog dailyLog : weekLogs) {
            String masked = maskingService.mask(dailyLog.getRawText(), MaskingProfile.INTERNAL).text();
            if (masked == null || masked.isBlank()) {
                continue;
            }
            sb.append('[').append(dailyLog.getWorkDate()).append("]\n").append(masked.trim()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
