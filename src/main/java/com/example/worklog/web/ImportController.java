package com.example.worklog.web;

import com.example.worklog.weekly.PastReportService;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 過去週報の CSV 取り込み。取り込んだ内容はこのマシンから出ない。 */
@Controller
@RequestMapping("/import")
public class ImportController {

    private final PastReportService service;

    public ImportController(PastReportService service) {
        this.service = service;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("reports", service.all());
        model.addAttribute("count", service.count());
        model.addAttribute("boilerplate", service.boilerplateCandidates());
        model.addAttribute("examples", service.distinctiveExamples(10));
        return "import";
    }

    @PostMapping
    public String upload(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("error", "ファイルが選択されていません。");
            return "redirect:/import";
        }
        try {
            PastReportService.ImportResult result = service.importCsv(file.getBytes());
            ra.addFlashAttribute("message",
                    "取り込みました: 新規 " + result.imported() + " 件 / 更新 " + result.updated() + " 件");
        } catch (IOException e) {
            ra.addFlashAttribute("error", "ファイルを読み取れませんでした: " + e.getMessage());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", "取り込みに失敗しました: " + e.getMessage());
        }
        return "redirect:/import";
    }
}
