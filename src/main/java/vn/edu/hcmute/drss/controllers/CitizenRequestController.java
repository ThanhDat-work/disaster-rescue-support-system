package vn.edu.hcmute.drss.controllers;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import vn.edu.hcmute.drss.dtos.CancelCitizenRequest;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.CitizenRequestService;

@Controller
@RequestMapping("/requests")
public class CitizenRequestController {
    private final CitizenRequestService service;
    public CitizenRequestController(CitizenRequestService service) { this.service = service; }

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page, Model model) {
        Page<RescueRequest> pageData = service.list(principal.userId(), page);
        model.addAttribute("pageData", pageData);
        model.addAttribute("pageUrl", "/requests?");
        model.addAttribute("pageQuery", "");
        return "pages/citizen/request-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("createCitizenRequest", new CreateCitizenRequest());
        model.addAttribute("disasterTypes", service.getActiveDisasterTypes());
        return "pages/citizen/request-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("createCitizenRequest") CreateCitizenRequest form,
            BindingResult binding, @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Model model, RedirectAttributes flash) {
        if (binding.hasErrors()) return showCreateForm(model);
        try {
            RescueRequest created = service.create(principal.userId(), form);
            flash.addFlashAttribute("successMsg", "Đã gửi yêu cầu " + created.getDisplayCode() + ".");
            return "redirect:/requests/" + created.getId();
        } catch (IllegalArgumentException exception) {
            flash.addFlashAttribute("errorMsg", exception.getMessage());
            return "redirect:/requests/new";
        }
    }

    @GetMapping("/{requestId}")
    public String detail(@PathVariable UUID requestId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Model model, RedirectAttributes flash) {
        try {
            model.addAttribute("request", service.detail(requestId, principal.userId()));
            model.addAttribute("history", service.history(requestId, principal.userId()));
            model.addAttribute("media", service.media(requestId, principal.userId()));
            model.addAttribute("cancelCitizenRequest", new CancelCitizenRequest());
            return "pages/citizen/request-detail";
        } catch (IllegalArgumentException exception) {
            flash.addFlashAttribute("errorMsg", exception.getMessage());
            return "redirect:/requests";
        }
    }

    @PostMapping("/{requestId}/cancel")
    public String cancel(@PathVariable UUID requestId,
            @Valid @ModelAttribute("cancelCitizenRequest") CancelCitizenRequest form,
            BindingResult binding, @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            RedirectAttributes flash) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute("errorMsg", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/requests/" + requestId;
        }
        try {
            service.cancel(requestId, principal.userId(), form.getReason());
            flash.addFlashAttribute("successMsg", "Đã hủy yêu cầu.");
        } catch (OptimisticLockingFailureException exception) {
            flash.addFlashAttribute("errorMsg", "Yêu cầu vừa được cập nhật. Tải lại trang để xem trạng thái mới.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            flash.addFlashAttribute("errorMsg", exception.getMessage());
        }
        return "redirect:/requests/" + requestId;
    }

    private String showCreateForm(Model model) {
        model.addAttribute("disasterTypes", service.getActiveDisasterTypes());
        return "pages/citizen/request-form";
    }
}
