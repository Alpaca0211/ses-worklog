package com.example.worklog.web;

import com.example.worklog.abstraction.AbstractionService;
import com.example.worklog.career.CareerDraft;
import com.example.worklog.career.CareerService;
import com.example.worklog.career.CareerTextFormatter;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** スキルシートの下書き生成。社外に出る文章のため、固有名詞はすべて伏せる。 */
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

        CareerDraft draft = service.build(begin, end, generate);
        model.addAttribute("from", begin);
        model.addAttribute("to", end);
        model.addAttribute("draft", draft);
        model.addAttribute("careerText", formatter.format(draft));
        model.addAttribute("projects", service.projects());
        model.addAttribute("technologies", service.technologies());
        model.addAttribute("llmStatus", abstractionService.status());
        model.addAttribute("generated", generate);
        return "career";
    }

    @PostMapping("/projects/{id}")
    public String updateProject(@PathVariable Long id,
                                @RequestParam(required = false) String industry,
                                @RequestParam(required = false) String publicDescription,
                                @RequestParam(required = false) String overview,
                                @RequestParam(required = false) String teamComposition,
                                @RequestParam(required = false) String projectScale,
                                @RequestParam(required = false) String environment,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                @RequestParam(name = "technologyIds", required = false) List<Long> technologyIds,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                RedirectAttributes ra) {
        service.updateProject(id, new CareerService.ProjectDetails(
                industry, publicDescription, overview, teamComposition, projectScale,
                environment, startDate, endDate, technologyIds));
        ra.addFlashAttribute("message", "案件の情報を更新しました");
        return "redirect:/career?from=" + from + "&to=" + to;
    }
}
