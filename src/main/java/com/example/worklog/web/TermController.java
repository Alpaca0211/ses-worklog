package com.example.worklog.web;

import com.example.worklog.domain.TermCategory;
import com.example.worklog.masking.TermService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/terms")
public class TermController {

    private final TermService termService;

    public TermController(TermService termService) {
        this.termService = termService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("terms", termService.findAll());
        model.addAttribute("categories", TermCategory.values());
        return "terms";
    }

    @PostMapping
    public String add(@RequestParam String term,
                      @RequestParam(required = false) String replacement,
                      @RequestParam TermCategory category,
                      RedirectAttributes ra) {
        try {
            termService.add(term, replacement, category);
            ra.addFlashAttribute("message", "登録しました: " + term.trim());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/terms";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        termService.toggle(id);
        ra.addFlashAttribute("message", "有効/無効を切り替えました");
        return "redirect:/terms";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        termService.delete(id);
        ra.addFlashAttribute("message", "削除しました");
        return "redirect:/terms";
    }
}
