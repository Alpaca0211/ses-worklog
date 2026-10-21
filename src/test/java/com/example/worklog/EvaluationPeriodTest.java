package com.example.worklog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.worklog.career.EvaluationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 評価期間の区切り。
 *
 * <p>査定は 6 月と 11 月。1 年を過不足なく二分できるのは 12 月〜5 月と 6 月〜11 月の組だけで、
 * 材料が「どの期のものか」を取り違えると、期末に足りない項目へ気付けなくなる。
 */
class EvaluationPeriodTest extends SanitizeTestBase {

    @Autowired
    EvaluationService service;

    private List<EvaluationService.Period> at(String date) {
        return service.periods(LocalDate.parse(date));
    }

    @Test
    void 進行中の期が先頭に来る() {
        var current = at("2026-10-21").get(0);

        assertThat(current.from()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(current.to()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(current.label()).contains("11月査定");
    }

    @Test
    void 年をまたぐ期は前年12月から始まる() {
        var current = at("2026-02-10").get(0);

        assertThat(current.from()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(current.to()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(current.label()).contains("6月査定");
    }

    @Test
    void 査定月の当月はその期を進行中として扱う() {
        assertThat(at("2026-11-05").get(0).to()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(at("2026-05-31").get(0).to()).isEqualTo(LocalDate.of(2026, 5, 31));
    }

    @Test
    void 期は重ならず隙間もない() {
        var periods = at("2026-10-21");

        for (int i = 0; i + 1 < periods.size(); i++) {
            assertThat(periods.get(i).from()).isEqualTo(periods.get(i + 1).to().plusDays(1));
        }
    }
}
