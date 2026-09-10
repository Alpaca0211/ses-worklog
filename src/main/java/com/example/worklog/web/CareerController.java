package com.example.worklog.web;

import com.example.worklog.abstraction.AbstractionService;
import com.example.worklog.career.CareerDraft;
import com.example.worklog.career.CareerService;
import com.example.worklog.career.CareerTextFormatter;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 職務経歴の下書き生成。社外に出る文章のため、固有名詞はすべて伏せる。 */
@Controller
@RequestMapping("/career")
public class CareerController {

    private final CareerService service;
    private final CareerTextFormatter formatter;
    private final AbstractionService abstractionService;

    public CareerController(CareerService service, CareerTextFormatter formatter,
                            AbstractionService abstractionService) {
        this.service = service;
        this.formatter = formatter;
        this.abstractionService = abstractionService;
    }

    @GetMapping
    public String index(@RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(defaultValue = "false") boolean generate,
                        Model model) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate begin = from == null ? end.minusMonths(6).withDayOfMonth(1) : from;

        model.addAttribute("from", begin);
        model.addAttribute("to", end);
        CareerDraft draft = service.build(begin, end, generate);
        model.addAttribute("draft", draft);
        model.addAttribute("careerText", formatter.format(draft));
        model.addAttribute("projects", service.projects());
        model.addAttribute("llmStatus", abstractionService.status());
        model.addAttribute("generated", generate);
        return "career";
    }

    @PostMapping("/projects/{id}/describe")
    public String describe(@PathVariable Long id,
                           @RequestParam(required = false) String description,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           RedirectAttributes ra) {
        service.describe(id, description);
        ra.addFlashAttribute("message", "社外向けの説明を更新しました");
        return "redirect:/career?from=" + from + "&to=" + to;
    }
}
