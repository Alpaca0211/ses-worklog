package com.example.worklog.masking;

import com.example.worklog.domain.ForbiddenTerm;
import com.example.worklog.domain.ForbiddenTermRepository;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import org.ahocorasick.trie.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 禁止用語辞書のインメモリ表現。
 *
 * <p>Aho-Corasick を使う理由: 日本語には語境界が無いため正規表現の \b が使えず、
 * かつ用語数が増えても走査が O(テキスト長) で済む。
 * {@code ignoreOverlaps} により「株式会社ABC」と「ABC」が競合した場合は
 * 最長一致（＝より具体的な方）が優先される。
 */
@Component
public class TermDictionary {

    private static final Logger log = LoggerFactory.getLogger(TermDictionary.class);

    private final ForbiddenTermRepository repository;

    private volatile Snapshot snapshot = new Snapshot(Trie.builder().build(), Map.of(), 0);

    public TermDictionary(ForbiddenTermRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void reload() {
        Map<String, String> replacements = new HashMap<>();
        Trie.TrieBuilder builder = Trie.builder().ignoreCase().ignoreOverlaps();
        int count = 0;
        for (ForbiddenTerm t : repository.findByEnabledTrue()) {
            String term = t.getTerm();
            if (term == null || term.isBlank()) {
                continue;
            }
            builder.addKeyword(term);
            // ignoreCase 時に Emit が返す keyword は小文字化されるため、小文字キーで引く
            replacements.put(term.toLowerCase(), t.getReplacement());
            count++;
        }
        snapshot = new Snapshot(builder.build(), Map.copyOf(replacements), count);
        log.info("禁止用語辞書をロードしました: {} 件", count);
    }

    Snapshot snapshot() {
        return snapshot;
    }

    public int size() {
        return snapshot.termCount();
    }

    record Snapshot(Trie trie, Map<String, String> replacements, int termCount) {
    }
}
