package com.example.worklog.career;

/** 月数の表記。スキルシートが「0年7ヶ月」の形で書かれているのに合わせる。 */
final class Durations {

    private Durations() {
    }

    static String format(int months) {
        int safe = Math.max(months, 0);
        return (safe / 12) + "年" + (safe % 12) + "ヶ月";
    }
}
