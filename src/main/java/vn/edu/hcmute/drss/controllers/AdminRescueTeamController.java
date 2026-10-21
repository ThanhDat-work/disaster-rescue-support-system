package vn.edu.hcmute.drss.controllers;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.drss.dtos.AdminRescueTeamRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.services.AdminRescueTeamService;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin/teams")
public class AdminRescueTeamController {

    private static final List<String> STATUSES = List.of(
            "AVAILABLE",
            "BUSY",
            "OFFLINE"
    );

    private final AdminRescueTeamService service;

    public AdminRescueTeamController(AdminRescueTeamService service) {
        this.service = service;
    }

    @ModelAttribute("teamStatuses")
    public List<String> teamStatuses() {
        return STATUSES;
    }

    @ModelAttribute("pageSizes")
    public List<Integer> pageSizes() {
        return List.of(10, 20, 50);
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        try {
            Page<RescueTeam> result = service.search(
                    keyword,
                    status,
                    page,
                    size
            );
            model.addAttribute("result", result);
        } catch (AdminDomainException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute(
                    "result",
                    service.search(null, null, 0, 20)
            );
            page = 0;
            size = 20;
        }

        model.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        model.addAttribute("status", status == null ? "" : status.trim());
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        return "pages/admin/team-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute(
                "form",
                new AdminRescueTeamRequest(
                        "",
                        "",
                        "",
                        "",
                        "AVAILABLE"
                )
        );
        model.addAttribute("editing", false);
return "pages/admin/team-form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") AdminRescueTeamRequest form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editing", false);
            return "pages/admin/team-form";
        }

        try {
            service.create(form);
        } catch (AdminDomainException exception) {
            reject(bindingResult, exception);
            model.addAttribute("editing", false);
            return "pages/admin/team-form";
        }

        redirectAttributes.addFlashAttribute(
                "successMsg",
                "Đã tạo đội cứu hộ"
        );
        return "redirect:/admin/teams";
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable UUID id,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            RescueTeam team = service.get(id);
            model.addAttribute(
                    "form",
                    new AdminRescueTeamRequest(
                            team.getDisplayCode(),
                            team.getTeamName(),
                            team.getLeaderName(),
                            team.getContactNumber(),
                            team.getStatus()
                    )
            );
            model.addAttribute("editing", true);
            model.addAttribute("teamId", id);
            return "pages/admin/team-form";
        } catch (AdminDomainException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return "redirect:/admin/teams";
        }
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("form") AdminRescueTeamRequest form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            prepareEditModel(model, id);
            return "pages/admin/team-form";
        }

        try {
            service.update(id, form);
        } catch (AdminDomainException exception) {
            reject(bindingResult, exception);
            prepareEditModel(model, id);
            return "pages/admin/team-form";
        }

        redirectAttributes.addFlashAttribute(
                "successMsg",
                "Đã cập nhật đội cứu hộ"
        );
        return "redirect:/admin/teams";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            RedirectAttributes redirectAttributes
    ) {
        try {
service.changeStatus(id, status);
            redirectAttributes.addFlashAttribute(
                    "successMsg",
                    "Đã cập nhật trạng thái đội cứu hộ"
            );
        } catch (AdminDomainException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
        }

        return "redirect:/admin/teams";
    }

    private void prepareEditModel(Model model, UUID id) {
        model.addAttribute("editing", true);
        model.addAttribute("teamId", id);
    }

    private void reject(
            BindingResult bindingResult,
            AdminDomainException exception
    ) {
        if (exception.getField() != null
                && bindingResult.getFieldError(exception.getField()) == null) {
            bindingResult.rejectValue(
                    exception.getField(),
                    "admin.domain",
                    exception.getMessage()
            );
            return;
        }

        bindingResult.reject(
                "admin.domain",
                exception.getMessage()
        );
    }
}
