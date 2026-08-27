package com.example.worklog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class DailyLogForm {

    @NotNull(message = "日付を入力してください")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate workDate = LocalDate.now();

    @NotBlank(message = "業務メモを入力してください")
    @Size(max = 5000, message = "5000文字以内で入力してください")
    private String rawText = "";

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }
}
