package com.example.worklog.weekly;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 作業内容欄の書式。
 *
 * <p>この書式は週報システムが定めたものではなく利用者が独自に決めているため
 * （システム側がマークダウン非対応であることへの対処）、ハードコードせず設定にする。
 * 既定値は現在の運用に合わせてある。
 */
@ConfigurationProperties(prefix = "worklog.format")
public record WorkFormatProperties(String dailyHeader, String projectPrefix, String itemPrefix) {

    public WorkFormatProperties {
        if (dailyHeader == null) {
            dailyHeader = "■やったこと";
        }
        if (projectPrefix == null) {
            projectPrefix = "▶ ";
        }
        if (itemPrefix == null) {
            itemPrefix = "・";
        }
    }
}
