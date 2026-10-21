package com.example.worklog.web;

import com.example.worklog.career.EvaluationService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 人事評価の材料の充足状況。期中に不足へ気付くための画面。 */
@Controller
@RequestMapping("/evaluation")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @GetMapping
    public String index(@RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        Model model) {
        // 既定は進行中の半期。査定の単位で見ないと「足りない項目」の意味が変わる
        EvaluationService.Period current = service.periods(LocalDate.now()).get(0);
        LocalDate begin = from == null ? current.from() : from;
        LocalDate end = to == null ? current.to() : to;

        model.addAttribute("periods", service.periods(LocalDate.now()));
        model.addAttribute("from", begin);
        model.addAttribute("to", end);
        model.addAttribute("summary", service.summarize(begin, end));
        model.addAttribute("untaggedLogs", service.untagged(begin, end));
        return "evaluation";
    }
}
