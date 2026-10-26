package vn.edu.hcmute.drss.controllers;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
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
import vn.edu.hcmute.drss.services.AdminRescueTeamService;
import vn.edu.hcmute.drss.services.AdminUserService;
import vn.edu.hcmute.drss.trace.RequestCorrelation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger(
            AdminUserController.class);

    private final AdminUserService service;
    private final AdminRescueTeamService teamService;

    @Autowired
    public AdminUserController(
            AdminUserService service,
            AdminRescueTeamService teamService
    ) {
        this.service = service;
        this.teamService = teamService;
    }

    public AdminUserController(AdminUserService service) {
        this.service = service;
        this.teamService = null;
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
            logDenied("STATUS_REJECTED", id, authentication, exception.getField());
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    referencedError(exception.getMessage()));
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
            logDenied("ROLES_VALIDATION", id, authentication, "roles");
            return renderRoleErrors(id, form, model, redirectAttributes);
        }

        try {
            service.updateRoles(
                    id,
                    form.getRoles(),
                    actorId(authentication));
        } catch (AdminDomainException exception) {
            logDenied("ROLES_REJECTED", id, authentication, exception.getField());
            if (exception.getField() != null) {
                bindingResult.rejectValue(
                        exception.getField(),
                        "admin.user",
                        referencedError(exception.getMessage()));
            } else {
                bindingResult.reject(
                        "admin.user",
                        referencedError(exception.getMessage()));
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
        if (teamService != null && user.roles().contains("ROLE_RESCUER")) {
            model.addAttribute(
                    "rescueMemberships",
                    teamService.findMembershipsForUser(user.id())
            );
        } else {
            model.addAttribute("rescueMemberships", List.of());
        }

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

    private String referencedError(String safeMessage) {
        String requestId = RequestCorrelation.currentId();
        return requestId == null ? safeMessage
                : safeMessage + " (Mã tham chiếu: " + requestId + ")";
    }

    private void logDenied(String action, UUID targetId,
            Authentication authentication, String field) {
        UUID actor = authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal
                ? principal.userId() : null;
        String reason = "roles".equals(field) || "status".equals(field)
                ? field : "domain";
        AUDIT_LOG.warn(
                "admin_user_action_denied requestId={} actorId={} targetId={} "
                        + "action={} reason={}",
                RequestCorrelation.currentId(), actor, targetId, action, reason);
    }
}