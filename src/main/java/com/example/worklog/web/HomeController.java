package com.example.worklog.web;

import com.example.worklog.abstraction.AbstractionService;
import com.example.worklog.domain.DailyLog;
import com.example.worklog.masking.TermService;
import com.example.worklog.pipeline.DailyLogService;
import com.example.worklog.pipeline.SanitizedResult;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private final DailyLogService logService;
    private final TermService termService;
    private final AbstractionService abstractionService;

    public HomeController(DailyLogService logService, TermService termService,
                          AbstractionService abstractionService) {
        this.logService = logService;
        this.termService = termService;
        this.abstractionService = abstractionService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("form", new DailyLogForm());
        return render(model);
    }

    @PostMapping("/preview")
    public String preview(@Valid @ModelAttribute("form") DailyLogForm form,
                          BindingResult binding, Model model) {
        if (!binding.hasErrors()) {
            SanitizedResult result = logService.preview(form.getRawText());
            model.addAttribute("preview", result);
        }
        return render(model);
    }

    @PostMapping("/logs")
    public String save(@Valid @ModelAttribute("form") DailyLogForm form,
                       BindingResult binding, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            return render(model);
        }
        DailyLog saved = logService.record(form.getWorkDate(), form.getRawText());
        ra.addFlashAttribute("message",
                "保存しました（" + saved.getStatus().getLabel() + " / 辞書置換 " + saved.getMaskedCount() + " 件）");
        return "redirect:/";
    }

    @GetMapping("/logs/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("log", logService.find(id));
        return "log-detail";
    }

    private String render(Model model) {
        model.addAttribute("logs", logService.recent(20));
        model.addAttribute("dictSize", termService.dictionarySize());
        model.addAttribute("llmStatus", abstractionService.status());
        return "index";
    }
}
