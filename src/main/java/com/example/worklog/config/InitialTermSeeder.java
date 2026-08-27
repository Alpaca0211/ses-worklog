package com.example.worklog.config;

import com.example.worklog.domain.ForbiddenTerm;
import com.example.worklog.domain.ForbiddenTermRepository;
import com.example.worklog.domain.TermCategory;
import com.example.worklog.masking.TermDictionary;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 初回起動時のみ、動作確認用のダミー用語を投入する。
 * 実運用では自分の現場の固有名詞に置き換えて使う。
 */
@Configuration
public class InitialTermSeeder {

    private static final Logger log = LoggerFactory.getLogger(InitialTermSeeder.class);

    @Bean
    ApplicationRunner seedTerms(ForbiddenTermRepository repository, TermDictionary dictionary) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            List<ForbiddenTerm> samples = List.of(
                    new ForbiddenTerm("サンプル商事", "顧客企業", TermCategory.CLIENT),
                    new ForbiddenTerm("サンプル商事株式会社", "顧客企業", TermCategory.CLIENT),
                    new ForbiddenTerm("決済API", "外部連携機能", TermCategory.SYSTEM),
                    new ForbiddenTerm("SampleCore", "担当システム", TermCategory.SYSTEM),
                    new ForbiddenTerm("次期基幹刷新PJ", "担当案件", TermCategory.PROJECT),
                    new ForbiddenTerm("田中", "チームメンバー", TermCategory.PERSON));
            repository.saveAll(samples);
            dictionary.reload();
            log.info("動作確認用の禁止用語を {} 件投入しました。/terms から自分の現場の語に置き換えてください。",
                    samples.size());
        };
    }
}
