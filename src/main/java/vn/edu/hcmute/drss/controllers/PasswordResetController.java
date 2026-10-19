package vn.edu.hcmute.drss.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.drss.dtos.ForgotPasswordRequest;
import vn.edu.hcmute.drss.dtos.NewPasswordRequest;
import vn.edu.hcmute.drss.dtos.ResetOtpRequest;
import vn.edu.hcmute.drss.security.ClientIpAddressResolver;
import vn.edu.hcmute.drss.services.PasswordResetCompletionResult;
import vn.edu.hcmute.drss.services.PasswordResetRequestResult;
import vn.edu.hcmute.drss.services.PasswordResetService;
import vn.edu.hcmute.drss.services.PasswordResetSessionService;
import vn.edu.hcmute.drss.services.PasswordResetVerificationResult;

import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/auth")
public class PasswordResetController {

    private final PasswordResetService resetService;
    private final PasswordResetSessionService sessionService;
    private final ClientIpAddressResolver ipAddressResolver;

    public PasswordResetController(
            PasswordResetService resetService,
            PasswordResetSessionService sessionService,
            ClientIpAddressResolver ipAddressResolver
    ) {
        this.resetService = resetService;
        this.sessionService = sessionService;
        this.ipAddressResolver = ipAddressResolver;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm(Model model) {
        if (!model.containsAttribute("forgotPasswordRequest")) {
            model.addAttribute(
                    "forgotPasswordRequest",
                    new ForgotPasswordRequest(null)
            );
        }

        return "pages/auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestReset(
            @Valid @ModelAttribute("forgotPasswordRequest")
            ForgotPasswordRequest forgotRequest,
            BindingResult bindingResult,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "pages/auth/forgot-password";
        }

        PasswordResetRequestResult result =
                resetService.requestReset(
                        forgotRequest.email(),
                        ipAddressResolver.resolve(request)
                );

        if (result.status()
                == PasswordResetRequestResult.Status.RATE_LIMITED) {
            bindingResult.reject(
                    "reset.rateLimited",
                    "Bạn thao tác quá nhanh. Vui lòng thử lại sau."
            );
            return "pages/auth/forgot-password";
        }

        sessionService.begin(request, forgotRequest.email());
        redirectAttributes.addFlashAttribute(
                "successMsg",
                "Nếu email hợp lệ, mã đặt lại mật khẩu đã được gửi."
        );

        return "redirect:/auth/reset-password/verify";
    }

    @GetMapping("/reset-password/verify")
    public String resetOtpForm(
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Optional<String> email = sessionService.pendingEmail(
                request.getSession(false)
        );

        if (email.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "Phiên đặt lại mật khẩu không còn hiệu lực. Vui lòng bắt đầu lại."
            );
            return "redirect:/auth/forgot-password";
        }

        if (!model.containsAttribute("resetOtpRequest")) {
            model.addAttribute(
                    "resetOtpRequest",
                    new ResetOtpRequest(null)
            );
        }

        model.addAttribute("maskedEmail", maskEmail(email.get()));
        return "pages/auth/reset-password-verify";
    }

    @PostMapping("/reset-password/verify")
    public String verifyResetOtp(
            @Valid @ModelAttribute("resetOtpRequest")
            ResetOtpRequest resetOtpRequest,
            BindingResult bindingResult,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        HttpSession session = request.getSession(false);
        Optional<String> email = sessionService.pendingEmail(session);

        if (email.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "Phiên đặt lại mật khẩu không còn hiệu lực. Vui lòng bắt đầu lại."
            );
            return "redirect:/auth/forgot-password";
        }

        model.addAttribute("maskedEmail", maskEmail(email.get()));

        if (bindingResult.hasErrors()) {
            return "pages/auth/reset-password-verify";
        }

        PasswordResetVerificationResult result =
                resetService.verifyResetOtp(
                        email.get(),
                        resetOtpRequest.otp(),
                        ipAddressResolver.resolve(request)
                );

        return switch (result.status()) {
            case VERIFIED -> {
                sessionService.establishGrant(
                        request,
                        result.verifiedTokenId()
                );
                yield "redirect:/auth/reset-password/new";
            }
            case INVALID -> {
                bindingResult.reject(
                        "resetOtp.invalid",
                        "Mã OTP không hợp lệ hoặc không còn hiệu lực."
                );
                yield "pages/auth/reset-password-verify";
            }
            case RATE_LIMITED -> {
                bindingResult.reject(
                        "resetOtp.rateLimited",
                        "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau."
                );
                yield "pages/auth/reset-password-verify";
            }
        };
    }

    @GetMapping("/reset-password/new")
    public String newPasswordForm(
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Optional<UUID> tokenId = validGrantTokenId(request);

        if (tokenId.isEmpty()) {
            clearAndExplain(request, redirectAttributes);
            return "redirect:/auth/forgot-password";
        }

        if (!model.containsAttribute("newPasswordRequest")) {
            model.addAttribute(
                    "newPasswordRequest",
                    new NewPasswordRequest(null, null)
            );
        }

        return "pages/auth/reset-password-new";
    }

    @PostMapping("/reset-password/new")
    public String completeReset(
            @Valid @ModelAttribute("newPasswordRequest")
            NewPasswordRequest newPasswordRequest,
            BindingResult bindingResult,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        Optional<UUID> tokenId = validGrantTokenId(request);

        if (tokenId.isEmpty()) {
            clearAndExplain(request, redirectAttributes);
            return "redirect:/auth/forgot-password";
        }

        if (bindingResult.hasErrors()) {
            return "pages/auth/reset-password-new";
        }

        PasswordResetCompletionResult result =
                resetService.completeReset(
                        tokenId.get(),
                        newPasswordRequest.newPassword(),
                        newPasswordRequest.confirmPassword()
                );

        return switch (result) {
            case COMPLETED -> {
                sessionService.clear(request.getSession(false));
                yield "redirect:/auth/login?reset";
            }
            case PASSWORD_POLICY_VIOLATION -> {
                bindingResult.rejectValue(
                        "newPassword",
                        "password.policy",
                        "Mật khẩu phải dài 12–64 ký tự và có chữ cái, chữ số."
                );
                yield "pages/auth/reset-password-new";
            }
            case PASSWORD_CONFIRMATION_MISMATCH -> {
                bindingResult.rejectValue(
                        "confirmPassword",
                        "password.confirmation",
                        "Mật khẩu xác nhận không khớp."
                );
                yield "pages/auth/reset-password-new";
            }
            case INVALID_GRANT -> {
                clearAndExplain(request, redirectAttributes);
                yield "redirect:/auth/forgot-password";
            }
        };
    }

    private Optional<UUID> validGrantTokenId(
            HttpServletRequest request
    ) {
        Optional<UUID> tokenId = sessionService.verifiedTokenId(
                request.getSession(false)
        );

        return tokenId.filter(resetService::isGrantValid);
    }

    private void clearAndExplain(
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        sessionService.clear(request.getSession(false));
        redirectAttributes.addFlashAttribute(
                "errorMsg",
                "Quyền đặt lại mật khẩu không còn hiệu lực. Vui lòng bắt đầu lại."
        );
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');

        if (atIndex <= 1) {
            return "***" + email.substring(Math.max(0, atIndex));
        }

        return email.substring(0, 1)
                + "***"
                + email.substring(atIndex);
    }
}