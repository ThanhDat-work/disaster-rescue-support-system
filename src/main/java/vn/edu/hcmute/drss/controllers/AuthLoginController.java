package vn.edu.hcmute.drss.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.hcmute.drss.dtos.LoginRequest;
import vn.edu.hcmute.drss.security.ClientIpAddressResolver;
import vn.edu.hcmute.drss.security.JwtCookieService;
import vn.edu.hcmute.drss.services.AccountLoginService;
import vn.edu.hcmute.drss.services.LoginResult;

@Controller
@RequestMapping("/auth")
public class AuthLoginController {

    private final AccountLoginService loginService;
    private final JwtCookieService jwtCookieService;
    private final ClientIpAddressResolver ipAddressResolver;

    public AuthLoginController(
            AccountLoginService loginService,
            JwtCookieService jwtCookieService,
            ClientIpAddressResolver ipAddressResolver
    ) {
        this.loginService = loginService;
        this.jwtCookieService = jwtCookieService;
        this.ipAddressResolver = ipAddressResolver;
    }

    @GetMapping("/login")
    public String loginForm(
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String activated,
            @RequestParam(required = false) String reset,
            Model model
    ) {
        if (!model.containsAttribute("loginRequest")) {
            model.addAttribute(
                    "loginRequest",
                    new LoginRequest(null, null)
            );
        }

        if (logout != null) {
            model.addAttribute(
                    "successMsg",
                    "Bạn đã đăng xuất an toàn."
            );
        } else if (activated != null) {
            model.addAttribute(
                    "successMsg",
                    "Tài khoản đã được kích hoạt. Bạn có thể đăng nhập."
            );
        } else if (reset != null) {
            model.addAttribute(
                    "successMsg",
                    "Mật khẩu đã được cập nhật. Bạn có thể đăng nhập lại."
            );
        }

        return "pages/auth/login";
    }

    @PostMapping("/login")
    public String login(
            @Valid @ModelAttribute("loginRequest")
            LoginRequest loginRequest,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            return "pages/auth/login";
        }

        LoginResult result = loginService.login(
                loginRequest,
                ipAddressResolver.resolve(request)
        );

        return switch (result.status()) {
            case AUTHENTICATED -> {
                ResponseCookie cookie =
                        jwtCookieService.createAccessTokenCookie(
                                result.accessToken()
                        );

                response.addHeader(
                        HttpHeaders.SET_COOKIE,
                        cookie.toString()
                );

                yield successfulLoginRedirect();
            }
            case INVALID_CREDENTIALS -> {
                bindingResult.reject(
                        "login.invalid",
                        "Email hoặc mật khẩu không đúng."
                );
                yield "pages/auth/login";
            }
            case INACTIVE -> {
                bindingResult.reject(
                        "login.inactive",
                        "Tài khoản chưa được kích hoạt."
                );
                yield "pages/auth/login";
            }
            case BANNED, NO_ACTIVE_ROLES -> {
                bindingResult.reject(
                        "login.unavailable",
                        "Tài khoản hiện không thể đăng nhập."
                );
                yield "pages/auth/login";
            }
            case RATE_LIMITED -> {
                bindingResult.reject(
                        "login.rateLimited",
                        "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau."
                );
                yield "pages/auth/login";
            }
        };
    }

    private String successfulLoginRedirect() {
        /*
         * T2-01 redirect về trang chủ công khai.
         * Task sau có thể thay method này bằng saved-request hoặc
         * role landing mà không sửa AccountLoginService.
         */
        return "redirect:/";
    }
}