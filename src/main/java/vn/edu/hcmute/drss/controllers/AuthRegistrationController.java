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
import vn.edu.hcmute.drss.dtos.ActivationRequest;
import vn.edu.hcmute.drss.dtos.RegistrationRequest;
import vn.edu.hcmute.drss.dtos.ResendOtpRequest;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.security.ClientIpAddressResolver;
import vn.edu.hcmute.drss.services.AccountActivationService;
import vn.edu.hcmute.drss.services.AccountRegistrationService;
import vn.edu.hcmute.drss.services.ActivationResult;
import vn.edu.hcmute.drss.services.OtpResendResult;
import vn.edu.hcmute.drss.services.OtpResendService;
import vn.edu.hcmute.drss.services.PasswordResetSessionService;
import vn.edu.hcmute.drss.services.RegistrationResult;

@Controller
@RequestMapping("/auth")
public class AuthRegistrationController {

    private final AccountRegistrationService registrationService;
    private final AccountActivationService activationService;
    private final OtpResendService resendService;
    private final PasswordResetSessionService resetSessionService;
    private final ClientIpAddressResolver ipAddressResolver;

    public AuthRegistrationController(
            AccountRegistrationService registrationService,
            AccountActivationService activationService,
            OtpResendService resendService,
            PasswordResetSessionService resetSessionService,
            ClientIpAddressResolver ipAddressResolver
    ) {
        this.registrationService = registrationService;
        this.activationService = activationService;
        this.resendService = resendService;
        this.resetSessionService = resetSessionService;
        this.ipAddressResolver = ipAddressResolver;
    }

    @GetMapping("/register")
    public String registrationForm(Model model) {
        if (!model.containsAttribute("registrationRequest")) {
            model.addAttribute(
                    "registrationRequest",
                    new RegistrationRequest(
                            null, null, null, null, null
                    )
            );
        }

        return "pages/auth/register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registrationRequest")
            RegistrationRequest registrationRequest,
            BindingResult bindingResult,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "pages/auth/register";
        }

        RegistrationResult result = registrationService.register(
                registrationRequest,
                ipAddressResolver.resolve(request)
        );

        return switch (result.status()) {
            case ACCEPTED -> {
                redirectAttributes.addFlashAttribute(
                        "successMsg",
                        "Nếu thông tin hợp lệ, mã kích hoạt đã được gửi tới email của bạn."
                );
                yield "redirect:/auth/verify-otp";
            }
            case EMAIL_DELIVERY_FAILED -> {
                redirectAttributes.addFlashAttribute(
                        "errorMsg",
                        "Thông tin đã được ghi nhận nhưng chưa thể gửi email. Vui lòng thử gửi lại OTP."
                );
                yield "redirect:/auth/verify-otp";
            }
            case RATE_LIMITED -> {
                bindingResult.reject(
                        "registration.rateLimited",
                        "Bạn thao tác quá nhanh. Vui lòng thử lại sau."
                );
                yield "pages/auth/register";
            }
        };
    }

    @GetMapping("/verify-otp")
    public String activationForm(Model model) {
        if (!model.containsAttribute("activationRequest")) {
            model.addAttribute(
                    "activationRequest",
                    new ActivationRequest(null, null)
            );
        }

        return "pages/auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String activate(
            @Valid @ModelAttribute("activationRequest")
            ActivationRequest activationRequest,
            BindingResult bindingResult,
            HttpServletRequest request
    ) {
        if (bindingResult.hasErrors()) {
            return "pages/auth/verify-otp";
        }

        ActivationResult result = activationService.activate(
                activationRequest.email(),
                activationRequest.otp(),
                ipAddressResolver.resolve(request)
        );

        return switch (result.status()) {
            case ACTIVATED ->
                    "redirect:/auth/login?activated";
            case INVALID -> {
                bindingResult.reject(
                        "activation.invalid",
                        "Email hoặc mã OTP không hợp lệ."
                );
                yield "pages/auth/verify-otp";
            }
            case RATE_LIMITED -> {
                bindingResult.reject(
                        "activation.rateLimited",
                        "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau."
                );
                yield "pages/auth/verify-otp";
            }
        };
    }

    @PostMapping("/resend-otp")
    public String resendOtp(
            @Valid @ModelAttribute
            ResendOtpRequest resendRequest,
            BindingResult bindingResult,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        VerificationTokenPurpose purpose = resendRequest.purpose();
        String fallbackRoute = purpose
                == VerificationTokenPurpose.PASSWORD_RESET
                ? "/auth/reset-password/verify"
                : "/auth/verify-otp";

        if (bindingResult.hasErrors() || purpose == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "Không thể gửi lại mã. Vui lòng kiểm tra thông tin và thử lại."
            );
            return "redirect:" + fallbackRoute;
        }

        String email = resendRequest.email();

        if (purpose == VerificationTokenPurpose.PASSWORD_RESET) {
            HttpSession session = request.getSession(false);
            email = resetSessionService.pendingEmail(session)
                    .orElse(null);

            if (email == null) {
                redirectAttributes.addFlashAttribute(
                        "errorMsg",
                        "Phiên đặt lại mật khẩu không còn hiệu lực. Vui lòng bắt đầu lại."
                );
                return "redirect:/auth/forgot-password";
            }
        }

        if (email == null || email.isBlank()) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "Không thể gửi lại mã. Vui lòng kiểm tra thông tin và thử lại."
            );
            return "redirect:" + fallbackRoute;
        }

        OtpResendResult result = resendService.resend(
                email,
                purpose,
                ipAddressResolver.resolve(request)
        );

        if (result.status() == OtpResendResult.Status.RATE_LIMITED) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "Bạn thao tác quá nhanh. Vui lòng thử lại sau."
            );
        } else {
            redirectAttributes.addFlashAttribute(
                    "successMsg",
                    "Nếu thông tin hợp lệ, một mã OTP mới đã được gửi."
            );
        }

        return "redirect:" + fallbackRoute;
    }
}