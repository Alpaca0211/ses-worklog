package com.example.worklog.weekly;

import com.example.worklog.domain.WorkEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 作業記録を、週報システムの作業内容欄に貼り付けられるテキストへ組み立てる。
 *
 * <p>構造化データからの単純な文字列生成であり、LLM は使わない。
 * 生成例:
 * <pre>
 * ■やったこと
 * ▶ 案件A
 * ・脆弱性対応（8月） リリース対応（service_a：stg、service_b：stg）
 *
 * ▶ 案件B
 * ・レビュー依頼（service_c）
 * </pre>
 */
@Service
public class WorkContentFormatter {

    private final WorkFormatProperties format;

    public WorkContentFormatter(WorkFormatProperties format) {
        this.format = format;
    }

    /** 1 日分の作業内容テキスト。記録が無ければ空文字。 */
    public String formatDay(List<WorkEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        // 案件ごとにまとめる。表示順は取得順（案件の displayOrder）を尊重する
        Map<String, List<WorkEntry>> byProject = entries.stream()
                .collect(Collectors.groupingBy(e -> e.getProject().getName(),
                        LinkedHashMap::new, Collectors.toList()));

        StringBuilder sb = new StringBuilder(format.dailyHeader());
        for (Map.Entry<String, List<WorkEntry>> group : byProject.entrySet()) {
            sb.append('\n').append(format.projectPrefix()).append(group.getKey());
            for (WorkEntry entry : group.getValue()) {
                sb.append('\n').append(format.itemPrefix()).append(formatItem(entry));
            }
            sb.append('\n'); // 案件間の空行
        }
        return sb.toString().stripTrailing();
    }

    /** 「施策名 作業種別（対象）」の 1 行。施策名・対象は未入力を許容する。 */
    private String formatItem(WorkEntry entry) {
        StringBuilder line = new StringBuilder();
        String workstream = trimToEmpty(entry.getWorkstream());
        if (!workstream.isEmpty()) {
            line.append(workstream).append(' ');
        }
        line.append(entry.getTaskType().getName());

        String targets = trimToEmpty(entry.getTargets());
        if (!targets.isEmpty()) {
            line.append('（').append(targets).append('）');
        }
        return line.toString();
    }

    private String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
