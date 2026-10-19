package vn.edu.hcmute.drss.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import vn.edu.hcmute.drss.security.JwtCookieService;

@Controller
public class AuthLogoutController {

    private final JwtCookieService jwtCookieService;

    public AuthLogoutController(
            JwtCookieService jwtCookieService
    ) {
        this.jwtCookieService = jwtCookieService;
    }

    @PostMapping("/auth/logout")
    public String logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        ResponseCookie deletionCookie =
                jwtCookieService.createDeletionCookie();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                deletionCookie.toString()
        );

        /*
         * Xóa cả CSRF token và trạng thái reset tạm nếu session
         * đang tồn tại. Không tạo session mới chỉ để logout.
         */
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        return "redirect:/auth/login?logout";
    }
}