package vn.edu.hcmute.drss.controllers;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
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
import vn.edu.hcmute.drss.dtos.AdminUserDetail;
import vn.edu.hcmute.drss.dtos.AdminUserRolesRequest;
import vn.edu.hcmute.drss.dtos.AdminUserSummary;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.AdminUserService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @ModelAttribute("pageSizes")
    public List<Integer> pageSizes() {
        return List.of(10, 20, 50);
    }

    @ModelAttribute("allowedRoles")
    public List<String> allowedRoles() {
        return service.allowedRoles();
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        try {
            Page<AdminUserSummary> result = service.search(
                    keyword,
                    status,
                    role,
                    page,
                    size);
            model.addAttribute("result", result);
        } catch (AdminDomainException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute(
                    "result",
                    service.search(null, null, null, 0, 20));
            page = 0;
            size = 20;
        }

        model.addAttribute(
                "keyword",
                keyword == null ? "" : keyword.trim());
        model.addAttribute("status", status);
        model.addAttribute("role", role);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        return "pages/admin/user-list";
    }

    @GetMapping("/{id}")
    public String detail(
            @PathVariable UUID id,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            prepareDetailModel(model, service.getDetail(id), null);
            return "pages/admin/user-detail";
        } catch (AdminDomainException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage());
            return "redirect:/admin/users";
        }
    }

    @PostMapping("/{id}/status")
    public String changeStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            service.changeStatus(id, status, actorId(authentication));
            redirectAttributes.addFlashAttribute(
                    "successMsg",
                    "Đã cập nhật trạng thái người dùng");
        } catch (AdminDomainException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage());
        }

        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/{id}/roles")
    public String updateRoles(
            @PathVariable UUID id,
            @Valid @ModelAttribute("rolesForm") AdminUserRolesRequest form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderRoleErrors(id, form, model, redirectAttributes);
        }

        try {
            service.updateRoles(
                    id,
                    form.getRoles(),
                    actorId(authentication));
        } catch (AdminDomainException exception) {
            if (exception.getField() != null) {
                bindingResult.rejectValue(
                        exception.getField(),
                        "admin.user",
                        exception.getMessage());
            } else {
                bindingResult.reject(
                        "admin.user",
                        exception.getMessage());
            }
            return renderRoleErrors(id, form, model, redirectAttributes);
        }

        redirectAttributes.addFlashAttribute(
                "successMsg",
                "Đã cập nhật vai trò người dùng");
        return "redirect:/admin/users/" + id;
    }

    private String renderRoleErrors(
            UUID id,
            AdminUserRolesRequest form,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            prepareDetailModel(model, service.getDetail(id), form);
            return "pages/admin/user-detail";
        } catch (AdminDomainException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage());
            return "redirect:/admin/users";
        }
    }

    private void prepareDetailModel(
            Model model,
            AdminUserDetail user,
            AdminUserRolesRequest submittedForm) {
        model.addAttribute("user", user);

        if (submittedForm == null) {
            model.addAttribute(
                    "rolesForm",
                    new AdminUserRolesRequest(Set.copyOf(user.roles())));
        }
    }

    private UUID actorId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
            throw new AdminDomainException(
                    null,
                    "Không xác định được quản trị viên đang đăng nhập");
        }

        return principal.userId();
    }
}