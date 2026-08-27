package com.example.worklog.web;

import com.example.worklog.domain.*;
import com.example.worklog.weekly.WorkContentFormatter;
import com.example.worklog.weekly.WorkEntryService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 定型作業の記録画面。文章を書かせず、マスタからの選択で入力を完了させる。 */
@Controller
@RequestMapping("/work")
public class WorkEntryController {

    private final WorkEntryService service;
    private final WorkContentFormatter formatter;
    private final ProjectRepository projectRepository;
    private final TaskTypeRepository taskTypeRepository;

    public WorkEntryController(WorkEntryService service, WorkContentFormatter formatter,
                               ProjectRepository projectRepository,
                               TaskTypeRepository taskTypeRepository) {
        this.service = service;
        this.formatter = formatter;
        this.projectRepository = projectRepository;
        this.taskTypeRepository = taskTypeRepository;
    }

    @GetMapping
    public String index(@RequestParam(required = false)
                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                        Model model) {
        LocalDate target = date == null ? LocalDate.now() : date;
        List<WorkEntry> entries = service.entriesOn(target);

        model.addAttribute("date", target);
        model.addAttribute("prevDate", target.minusDays(1));
        model.addAttribute("nextDate", target.plusDays(1));
        model.addAttribute("entries", entries);
        model.addAttribute("preview", formatter.formatDay(entries));
        model.addAttribute("projects", service.projects());
        model.addAttribute("taskTypes", service.taskTypes());
        model.addAttribute("workstreams", service.workstreamSuggestions());
        model.addAttribute("allProjects", projectRepository.findAll());
        return "work-entries";
    }

    @PostMapping
    public String add(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      @RequestParam Long projectId,
                      @RequestParam(required = false) String workstream,
                      @RequestParam Long taskTypeId,
                      @RequestParam(required = false) String targets,
                      RedirectAttributes ra) {
        service.add(date, projectId, workstream, taskTypeId, targets);
        ra.addFlashAttribute("message", "記録しました");
        return "redirect:/work?date=" + date;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         RedirectAttributes ra) {
        service.delete(id);
        ra.addFlashAttribute("message", "削除しました");
        return "redirect:/work?date=" + date;
    }

    @PostMapping("/projects")
    public String addProject(@RequestParam String name,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             RedirectAttributes ra) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            ra.addFlashAttribute("error", "案件名が空です。");
        } else {
            int order = projectRepository.findAll().size() + 1;
            projectRepository.save(new Project(trimmed, order));
            ra.addFlashAttribute("message", "案件を追加しました: " + trimmed);
        }
        return "redirect:/work?date=" + date;
    }

    @PostMapping("/projects/{id}/toggle")
    public String toggleProject(@PathVariable Long id,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        projectRepository.findById(id).ifPresent(p -> {
            p.setActive(!p.isActive());
            projectRepository.save(p);
        });
        return "redirect:/work?date=" + date;
    }

    @PostMapping("/task-types")
    public String addTaskType(@RequestParam String name,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              RedirectAttributes ra) {
        String trimmed = name == null ? "" : name.trim();
        if (!trimmed.isEmpty()) {
            taskTypeRepository.save(new TaskType(trimmed, taskTypeRepository.findAll().size() + 1));
            ra.addFlashAttribute("message", "作業種別を追加しました: " + trimmed);
        }
        return "redirect:/work?date=" + date;
    }
}
