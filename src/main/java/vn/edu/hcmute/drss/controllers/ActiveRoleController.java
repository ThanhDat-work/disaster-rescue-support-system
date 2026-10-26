package vn.edu.hcmute.drss.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.hcmute.drss.dtos.InitialRoleSelectionRequest;
import vn.edu.hcmute.drss.dtos.RoleSwitchRequest;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.security.ClientIpAddressResolver;
import vn.edu.hcmute.drss.security.JwtCookieService;
import vn.edu.hcmute.drss.services.ActiveRoleChangeResult;
import vn.edu.hcmute.drss.services.ActiveRoleService;
import vn.edu.hcmute.drss.services.InitialRoleSelectionSessionService;
import vn.edu.hcmute.drss.services.RoleSwitchService;

import java.util.UUID;

@Controller
public class ActiveRoleController {

    private final ActiveRoleService activeRoleService;
    private final RoleSwitchService roleSwitchService;
    private final InitialRoleSelectionSessionService sessionService;
    private final JwtCookieService cookieService;
    private final ClientIpAddressResolver ipAddressResolver;

    public ActiveRoleController(
            ActiveRoleService activeRoleService,
            RoleSwitchService roleSwitchService,
            InitialRoleSelectionSessionService sessionService,
            JwtCookieService cookieService,
            ClientIpAddressResolver ipAddressResolver
    ) {
        this.activeRoleService = activeRoleService;
        this.roleSwitchService = roleSwitchService;
        this.sessionService = sessionService;
        this.cookieService = cookieService;
        this.ipAddressResolver = ipAddressResolver;
    }

    @GetMapping("/auth/select-role")
    public String initialRoleForm(
            HttpSession session,
            Model model
    ) {
        UUID userId = sessionService.resolve(session)
                .orElse(null);
        if (userId == null) {
            return "redirect:/auth/login";
        }

        if (!model.containsAttribute("roleForm")) {
            model.addAttribute(
                    "roleForm",
                    new InitialRoleSelectionRequest(null)
            );
        }
        model.addAttribute(
                "availableRoles",
                activeRoleService.activeRoles(userId)
        );
        return "pages/auth/select-role";
    }

    @PostMapping("/auth/select-role")
    public String selectInitialRole(
            @Valid @ModelAttribute("roleForm")
            InitialRoleSelectionRequest form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        UUID userId = sessionService
                .resolve(request.getSession(false))
                .orElse(null);
        if (userId == null) {
            return "redirect:/auth/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute(
                    "availableRoles",
                    activeRoleService.activeRoles(userId)
            );
            return "pages/auth/select-role";
        }

        ActiveRoleChangeResult result =
                activeRoleService.selectInitialRole(
                        userId,
                        form.targetRole(),
                        ipAddressResolver.resolve(request)
                );
        if (result.status()
                != ActiveRoleChangeResult.Status.SUCCESS) {
            bindingResult.reject(
                    "role.invalid",
                    "Không thể chọn vai trò này. Vui lòng đăng nhập lại."
            );
            model.addAttribute(
                    "availableRoles",
                    activeRoleService.activeRoles(userId)
            );
            return "pages/auth/select-role";
        }

        addCookie(response, result);
        sessionService.clear(request.getSession(false));
        return "redirect:" + result.redirectPath();
    }

    @GetMapping("/account/role-switch")
    public String roleSwitchForm(
            Authentication authentication,
            Model model,
            @RequestParam(name = "targetRole", required = false)
            String targetRole,
            @RequestParam(name = "returnTo", required = false)
            String returnTo
    ) {
        AuthenticatedUserPrincipal principal = principal(authentication);
        prepareSwitchModel(model, principal, targetRole, returnTo);
        return "pages/account/role-switch";
    }

    @PostMapping("/account/role-switch")
    public String switchRole(
            @Valid @ModelAttribute("roleSwitchRequest")
            RoleSwitchRequest form,
            BindingResult bindingResult,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            @RequestParam(name = "returnTo", required = false)
            String returnTo
    ) {
        AuthenticatedUserPrincipal principal = principal(authentication);

        if (bindingResult.hasErrors()) {
            prepareSwitchModel(model, principal, form.targetRole(), returnTo);
            return "pages/account/role-switch";
        }

        ActiveRoleChangeResult result = roleSwitchService.switchRole(
                principal,
                form.targetRole(),
                form.password(),
                ipAddressResolver.resolve(request)
        );

        if (result.status()
                == ActiveRoleChangeResult.Status.RATE_LIMITED) {
            bindingResult.reject(
                    "role.rateLimited",
                    "Bạn đã xác nhận sai quá nhiều lần. Vui lòng thử lại sau."
            );
        } else if (result.status()
                == ActiveRoleChangeResult.Status.INVALID) {
            bindingResult.reject(
                    "role.invalid",
                    "Không thể chuyển vai trò. Kiểm tra mật khẩu và lựa chọn của bạn."
            );
        } else {
            addCookie(response, result);
            String destination = safeRoleReturnPath(
                    returnTo,
                    form.targetRole()
            );
            return "redirect:" + (destination == null
                    ? result.redirectPath()
                    : destination);
        }

        prepareSwitchModel(model, principal, form.targetRole(), returnTo);
        return "pages/account/role-switch";
    }

    private void prepareSwitchModel(
            Model model,
            AuthenticatedUserPrincipal principal,
            String requestedRole,
            String returnTo
    ) {
        var availableRoles = activeRoleService.activeRoles(principal.userId());
        String normalizedRequestedRole = ActiveRoleService.normalizeRole(
                requestedRole
        );
        if (normalizedRequestedRole != null
                && (!availableRoles.contains(normalizedRequestedRole)
                || normalizedRequestedRole.equals(principal.activeRole()))) {
            normalizedRequestedRole = null;
        }
        if (!model.containsAttribute("roleSwitchRequest")) {
            model.addAttribute(
                    "roleSwitchRequest",
                    new RoleSwitchRequest(normalizedRequestedRole, null)
            );
        }
        model.addAttribute("activeRole", principal.activeRole());
        model.addAttribute("availableRoles", availableRoles);
        model.addAttribute(
                "roleSwitchReturnTo",
                safeRoleReturnPath(returnTo, normalizedRequestedRole)
        );
    }

    private String safeRoleReturnPath(String returnTo, String targetRole) {
        if (returnTo == null || returnTo.isBlank()) return null;
        String normalizedRole = ActiveRoleService.normalizeRole(targetRole);
        if (normalizedRole == null) return null;
        String prefix = switch (normalizedRole) {
            case ActiveRoleService.ROLE_CITIZEN -> "/requests/";
            case ActiveRoleService.ROLE_COORDINATOR -> "/coordinator/requests/";
            case ActiveRoleService.ROLE_RESCUER -> "/team/tasks/";
            default -> null;
        };
        if (prefix == null || !returnTo.startsWith(prefix)) return null;

        String id = returnTo.substring(prefix.length());
        if (id.contains("/") || id.contains("?") || id.contains("#")) return null;
        try {
            UUID requestId = UUID.fromString(id);
            if (!requestId.toString().equalsIgnoreCase(id)) return null;
            return prefix + requestId;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private AuthenticatedUserPrincipal principal(
            Authentication authentication
    ) {
        if (authentication == null
                || !(authentication.getPrincipal()
                instanceof AuthenticatedUserPrincipal principal)) {
            throw new IllegalStateException(
                    "Authenticated user is required"
            );
        }
        return principal;
    }

    private void addCookie(
            HttpServletResponse response,
            ActiveRoleChangeResult result
    ) {
        ResponseCookie cookie = cookieService
                .createAccessTokenCookie(result.accessToken());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }
}
