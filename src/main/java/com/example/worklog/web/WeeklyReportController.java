package com.example.worklog.web;

import com.example.worklog.abstraction.AbstractionService;
import com.example.worklog.weekly.ReportWeek;
import com.example.worklog.weekly.WeeklyReportService;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 週報の下書き生成画面。生成するだけで、週報システムへの書き込みは行わない。 */
@Controller
@RequestMapping("/weekly")
public class WeeklyReportController {

    private final WeeklyReportService service;
    private final AbstractionService abstractionService;

    public WeeklyReportController(WeeklyReportService service, AbstractionService abstractionService) {
        this.service = service;
        this.abstractionService = abstractionService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer week,
                        @RequestParam(required = false) Long templateId,
                        @RequestParam(defaultValue = "false") boolean generate,
                        Model model) {
        ReportWeek target = (year == null || month == null || week == null)
                ? ReportWeek.of(LocalDate.now())
                : new ReportWeek(year, month, week);

        var templates = service.templates();
        Long chosen = templateId != null ? templateId
                : (templates.isEmpty() ? null : templates.get(0).getId());

        model.addAttribute("report", service.build(target, chosen, generate));
        model.addAttribute("week", target);
        model.addAttribute("prev", target.previous());
        model.addAttribute("next", target.next());
        model.addAttribute("templates", templates);
        model.addAttribute("templateId", chosen);
        model.addAttribute("llmStatus", abstractionService.status());
        model.addAttribute("generated", generate);
        return "weekly-report";
    }
}
