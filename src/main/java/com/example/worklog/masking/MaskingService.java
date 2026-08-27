package com.example.worklog.masking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ahocorasick.trie.Emit;
import org.springframework.stereotype.Service;

/**
 * 辞書による決定論的マスキング。
 *
 * <p>ここに LLM は一切関与しない。秘匿化の一次防衛線は必ず決定論的でなければならず、
 * LLM に「固有名詞を隠して」と頼む方式は取りこぼしが避けられないため採用しない。
 */
@Service
public class MaskingService {

    private final TermDictionary dictionary;

    public MaskingService(TermDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public MaskingResult mask(String input) {
        if (input == null || input.isEmpty()) {
            return new MaskingResult(input == null ? "" : input, Map.of(), 0);
        }
        TermDictionary.Snapshot snap = dictionary.snapshot();
        if (snap.termCount() == 0) {
            return new MaskingResult(input, Map.of(), 0);
        }

        List<Emit> emits = new ArrayList<>(snap.trie().parseText(input));
        if (emits.isEmpty()) {
            return new MaskingResult(input, Map.of(), 0);
        }
        emits.sort(Comparator.comparingInt(Emit::getStart));

        StringBuilder sb = new StringBuilder(input.length());
        Map<String, Integer> hits = new LinkedHashMap<>();
        int cursor = 0;
        for (Emit emit : emits) {
            if (emit.getStart() < cursor) {
                continue; // ignoreOverlaps でも念のため二重置換を防ぐ
            }
            sb.append(input, cursor, emit.getStart());

            String keyword = emit.getKeyword();
            String replacement = snap.replacements().getOrDefault(keyword.toLowerCase(), "（マスク）");
            sb.append(replacement);

            // 元テキスト側の表記でカウントする（大文字小文字の揺れを可視化するため）
            String original = input.substring(emit.getStart(), emit.getEnd() + 1);
            hits.merge(original, 1, Integer::sum);

            cursor = emit.getEnd() + 1;
        }
        sb.append(input, cursor, input.length());

        int total = hits.values().stream().mapToInt(Integer::intValue).sum();
        return new MaskingResult(sb.toString(), Map.copyOf(hits), total);
    }

    /** 出力に禁止用語が残っていないかの検査。残存していれば用語リストを返す。 */
    public List<String> detectLeaks(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        TermDictionary.Snapshot snap = dictionary.snapshot();
        if (snap.termCount() == 0) {
            return List.of();
        }
        return snap.trie().parseText(text).stream()
                .map(e -> text.substring(e.getStart(), e.getEnd() + 1))
                .distinct()
                .toList();
    }
}
