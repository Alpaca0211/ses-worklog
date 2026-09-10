package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.domain.PastWeeklyReportRepository;
import com.example.worklog.weekly.PastReportService;
import java.nio.charset.Charset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 過去週報の取り込みを、実際に永続化するところまで通す。
 *
 * <p>パーサ単体のテストだけでは、テーブル生成に失敗していても気付けない
 * （実際に year / month が予約語であることを見落として作成に失敗していた）。
 * DB へ書いて読み戻すところまで通すことで、スキーマの不備を検出する。
 */
class PastReportImportTest extends SanitizeTestBase {

    @Autowired
    PastReportService service;

    @Autowired
    PastWeeklyReportRepository repository;

    @BeforeEach
    void clear() {
        // インメモリDBはテストクラス間で共有されるため、件数を数える前に空にする
        repository.deleteAll();
    }

    /** 1 文目が使い回され、2 文目だけが週ごとに異なるという実際の書かれ方を再現する。 */
    private static final String BOILERPLATE = "依頼された業務について、作業を滞りなく遂行することができている。";

    private static byte[] csv() {
        StringBuilder sb = new StringBuilder("\"社員名\"\n\"架空 太郎\"\n");
        String[] distinctive = {
                "リリース作業において、効率化のため手順書の整備を行った。",
                "動作確認手順の変更に伴い、手順の追記作業を実施した。",
                "", // 定型文だけの週
                ""};
        for (int i = 0; i < distinctive.length; i++) {
            sb.append("\"年月週\",\"直近で学んだこと、覚えたこと\",\"コメント\",\"リーダーからの返信\",\"社長からの返信\",\"その他の返信\"\n");
            sb.append("\"2026年5月").append(i + 1).append("週\",\"学んだこと").append(i).append("\",\"【業務遂行】\n")
                    .append(BOILERPLATE).append('\n');
            if (!distinctive[i].isEmpty()) {
                sb.append(distinctive[i]).append('\n');
            }
            sb.append("\n【報連相】\n共有を行った。\n\",\"返信\",\"\",\"\"\n");
            sb.append("\"日付\",\"作業内容\",\"\",\"\",\"\",\"\"\n");
            sb.append("\"2026年5月").append(i + 1).append("日\",\"\",\"\",\"\",\"\",\"\"\n");
        }
        return sb.toString().getBytes(Charset.forName("windows-31j"));
    }

    @Test
    void 取り込んだ内容をDBに保存して読み戻せる() {
        PastReportService.ImportResult result = service.importCsv(csv());

        assertThat(result.imported()).isEqualTo(4);
        assertThat(service.count()).isEqualTo(4);
        assertThat(service.all()).extracting(r -> r.getLabel())
                .contains("2026年5月1週", "2026年5月4週");
    }

    @Test
    void 同じ週を取り込み直すと更新になる() {
        service.importCsv(csv());
        PastReportService.ImportResult second = service.importCsv(csv());

        assertThat(second.imported()).isZero();
        assertThat(second.updated()).isEqualTo(4);
        assertThat(service.count()).isEqualTo(4);
    }

    @Test
    void 使い回された行は定型文として文体例から除外される() {
        service.importCsv(csv());

        List<String> examples = service.distinctiveExamples(10);

        // 4 週すべてに現れる 1 文目は例に含めない
        assertThat(examples).doesNotContain(BOILERPLATE);
        assertThat(examples).contains(
                "リリース作業において、効率化のため手順書の整備を行った。",
                "動作確認手順の変更に伴い、手順の追記作業を実施した。");
    }

    @Test
    void 使い回された行は定型文の候補として提示される() {
        service.importCsv(csv());

        assertThat(service.boilerplateCandidates()).contains(BOILERPLATE);
    }
}
