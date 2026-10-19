package vn.edu.hcmute.drss.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import vn.edu.hcmute.drss.security.JwtAuthenticationService;
import vn.edu.hcmute.drss.security.JwtCookieAuthenticationFilter;

@Configuration
public class ApplicationSecurityConfig {

    @Bean
    JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter(
            JwtAuthenticationService authenticationService,
            AuthSecurityProperties properties
    ) {
        return new JwtCookieAuthenticationFilter(
                authenticationService,
                properties
        );
    }

    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            JwtCookieAuthenticationFilter jwtFilter
    ) throws Exception {
        AuthenticationEntryPoint loginEntryPoint =
                new LoginUrlAuthenticationEntryPoint(
                        "/auth/login"
                );

        return http
                /*
                * Authentication hoàn toàn dựa trên JWT và được dựng lại
                * ở từng request, không lưu SecurityContext trong HttpSession.
                *
                * STATELESS chỉ áp dụng cho trạng thái authentication.
                * HttpSession vẫn được HttpSessionCsrfTokenRepository và
                * password-reset grant sử dụng độc lập.
                */
                .securityContext(securityContext ->
                        securityContext.securityContextRepository(
                                new NullSecurityContextRepository()
                        )
                )
                /*
                * Không dùng Spring Security để quản lý phiên đăng nhập.
                *
                * Authentication được dựng lại từ JWT ở mỗi request và không được lưu
                * trong HttpSession vì SecurityContextRepository là Null.
                *
                * Tắt SessionManagementConfigurer cũng ngăn SessionManagementFilter
                * xem JWT authentication là một lần đăng nhập mới rồi thay session ID
                * và CSRF token ở mỗi request.
                *
                * HttpSession vẫn được phép tồn tại độc lập cho CSRF token và
                * password-reset grant.
                */
                .sessionManagement(AbstractHttpConfigurer::disable)
                /*
                 * Không lưu request đích vào session vì sau đăng nhập,
                 * ứng dụng tự điều hướng theo role đã chốt.
                 */
                .requestCache(requestCache ->
                        requestCache.requestCache(
                                new NullRequestCache()
                        )
                )
                /*
                 * CSRF giữ cấu hình mặc định dựa trên session.
                 * Không ignore các endpoint nghiệp vụ.
                 */
                .csrf(Customizer.withDefaults())
                /*
                 * Không dùng LogoutFilter mặc định.
                 * POST /auth/logout được xử lý bởi controller và vẫn
                 * phải đi qua CSRF + authorization.
                 */
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/error",
                                "/favicon.ico",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**"
                        ).permitAll()

                        /*
                         * Đặt logout trước các route auth công khai.
                         */
                        .requestMatchers(
                                "/auth/logout"
                        ).authenticated()

                        .requestMatchers(
                                "/auth/register",
                                "/auth/register/**",
                                "/auth/verify-otp",
                                "/auth/resend-otp",
                                "/auth/login",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/auth/reset-password/**"
                        ).permitAll()

                        .requestMatchers(
                                "/admin/**"
                        ).hasAuthority("ROLE_ADMIN")

                        .requestMatchers(
                                "/coordinator/**"
                        ).hasAuthority("ROLE_COORDINATOR")

                        .requestMatchers(
                                "/team/**"
                        ).hasAuthority("ROLE_RESCUER")

                        .requestMatchers(
                                "/requests/**"
                        ).hasAuthority("ROLE_CITIZEN")

                        .requestMatchers(
                                "/profile",
                                "/profile/**"
                        ).authenticated()

                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(
                                loginEntryPoint
                        )
                )
                /*
                 * Filter JWT phải chạy trước khi Spring tạo
                 * AnonymousAuthenticationToken.
                 */
                .addFilterBefore(
                        jwtFilter,
                        AnonymousAuthenticationFilter.class
                )
                .build();
    }
}