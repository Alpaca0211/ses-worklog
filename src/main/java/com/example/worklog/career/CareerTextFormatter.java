package com.example.worklog.career;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 職務経歴エントリを、職務経歴書へ貼り付けられるテキストへ組み立てる。
 * 構造化データからの文字列生成であり、LLM は使わない。
 */
@Service
public class CareerTextFormatter {

    public String format(CareerDraft draft) {
        if (draft == null || draft.isEmpty()) {
            return "";
        }
        List<CareerEntry> entries = draft.entries();
        StringBuilder sb = new StringBuilder();
        for (CareerEntry e : entries) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("【担当案件】").append(e.publicLabel()).append('\n');
            sb.append("【期間】").append(e.period()).append('\n');

            String tasks = e.taskSummary();
            if (!tasks.isBlank()) {
                sb.append("【担当業務】").append(tasks).append('\n');
            }
        }
        if (draft.hasAchievements()) {
            // 案件別ではなく期間全体の取り組みとして示す。
            // 日次メモを案件に紐付けていない以上、特定の案件のものとは言えないため。
            sb.append("\n【取り組み・実績】").append('\n');
            for (String a : draft.achievements()) {
                sb.append('・').append(a).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }
}
