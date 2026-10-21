package com.example.worklog.pipeline;

/** メモが保存された。抽象化はこのイベントを受けて後から行う。 */
public record DailyLogRecorded(Long logId) {
}
