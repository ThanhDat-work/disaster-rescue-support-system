package vn.edu.hcmute.drss.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCookieServiceTest {

    @Test
    void createsLocalHttpOnlySameSiteLaxCookie() {
        JwtCookieService service =
                new JwtCookieService(properties(false));

        ResponseCookie cookie =
                service.createAccessTokenCookie(
                        new IssuedAccessToken(
                                "signed.jwt.value",
                                Instant.parse(
                                        "2026-10-19T07:30:00Z"
                                )
                        )
                );

        assertThat(cookie.getName())
                .isEqualTo("DRSS_ACCESS_TOKEN");
        assertThat(cookie.getValue())
                .isEqualTo("signed.jwt.value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge())
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(cookie.getDomain()).isNull();
    }

    @Test
    void productionCookieUsesSecureFlag() {
        JwtCookieService service =
                new JwtCookieService(properties(true));

        ResponseCookie cookie =
                service.createAccessTokenCookie(
                        new IssuedAccessToken(
                                "signed.jwt.value",
                                Instant.parse(
                                        "2026-10-19T07:30:00Z"
                                )
                        )
                );

        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    void deletionCookiePreservesSecurityAttributes() {
        JwtCookieService service =
                new JwtCookieService(properties(true));

        ResponseCookie cookie =
                service.createDeletionCookie();

        assertThat(cookie.getName())
                .isEqualTo("DRSS_ACCESS_TOKEN");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
    }

    private AuthSecurityProperties properties(
            boolean secure
    ) {
        return new AuthSecurityProperties(
                "test-jwt-secret-at-least-32-bytes-aaaa",
                "test-otp-secret-at-least-32-bytes-bbbb",
                "test-rate-limit-secret-at-least-32-cccc",
                "drss",
                Duration.ofMinutes(30),
                "DRSS_ACCESS_TOKEN",
                secure,
                Duration.ofMinutes(5),
                5,
                "http://localhost:8080",
                "noreply@drss.example"
        );
    }
}