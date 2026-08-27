package com.example.worklog.masking;

import com.example.worklog.domain.ForbiddenTerm;
import com.example.worklog.domain.ForbiddenTermRepository;
import com.example.worklog.domain.TermCategory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermService {

    private final ForbiddenTermRepository repository;
    private final TermDictionary dictionary;

    public TermService(ForbiddenTermRepository repository, TermDictionary dictionary) {
        this.repository = repository;
        this.dictionary = dictionary;
    }

    @Transactional(readOnly = true)
    public List<ForbiddenTerm> findAll() {
        return repository.findAllByOrderByCategoryAscTermAsc();
    }

    /** 登録済みなら何もしない。辞書はその場で再構築する。 */
    @Transactional
    public ForbiddenTerm add(String term, String replacement, TermCategory category) {
        String normalized = term == null ? "" : term.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("用語が空です。");
        }
        ForbiddenTerm saved = repository.findByTerm(normalized)
                .orElseGet(() -> repository.save(new ForbiddenTerm(normalized, replacement, category)));
        dictionary.reload();
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
        dictionary.reload();
    }

    @Transactional
    public void toggle(Long id) {
        ForbiddenTerm t = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用語が見つかりません: " + id));
        t.setEnabled(!t.isEnabled());
        repository.save(t);
        dictionary.reload();
    }

    public int dictionarySize() {
        return dictionary.size();
    }
}
