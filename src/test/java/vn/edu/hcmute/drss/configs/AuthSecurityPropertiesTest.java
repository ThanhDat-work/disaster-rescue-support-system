package vn.edu.hcmute.drss.configs;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthSecurityPropertiesTest {

    private static final String VALID_JWT_SECRET =
            "jwt-secret-for-tests-12345678901234567890";

    private static final String VALID_OTP_SECRET =
            "otp-secret-for-tests-abcdefghij1234567890";

    @Test
    void acceptsValidSecurityConfiguration() {
        AuthSecurityProperties properties = createProperties(
                VALID_JWT_SECRET,
                VALID_OTP_SECRET,
                Duration.ofMinutes(30),
                Duration.ofMinutes(5),
                5,
                "http://localhost:8080"
        );

        assertThat(properties.issuer()).isEqualTo("drss");
        assertThat(properties.cookieName())
                .isEqualTo("DRSS_ACCESS_TOKEN");
        assertThat(properties.accessTokenTtl())
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.otpTtl())
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.otpMaxAttempts()).isEqualTo(5);
    }

    @Test
    void rejectsSecretShorterThanThirtyTwoBytes() {
        assertThatThrownBy(() -> createProperties(
                "too-short",
                VALID_OTP_SECRET,
                Duration.ofMinutes(30),
                Duration.ofMinutes(5),
                5,
                "http://localhost:8080"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT secret")
                .hasMessageContaining("32");
    }

    @Test
    void rejectsReusingOneSecretForJwtAndOtp() {
        assertThatThrownBy(() -> createProperties(
                VALID_JWT_SECRET,
                VALID_JWT_SECRET,
                Duration.ofMinutes(30),
                Duration.ofMinutes(5),
                5,
                "http://localhost:8080"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be different");
    }

    @Test
    void rejectsNonPositiveTokenLifetime() {
        assertThatThrownBy(() -> createProperties(
                VALID_JWT_SECRET,
                VALID_OTP_SECRET,
                Duration.ZERO,
                Duration.ofMinutes(5),
                5,
                "http://localhost:8080"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("access-token TTL");
    }

    @Test
    void rejectsNonPositiveOtpAttemptLimit() {
        assertThatThrownBy(() -> createProperties(
                VALID_JWT_SECRET,
                VALID_OTP_SECRET,
                Duration.ofMinutes(30),
                Duration.ofMinutes(5),
                0,
                "http://localhost:8080"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OTP maximum attempts");
    }

    @Test
    void rejectsBaseUrlWithoutHttpOrHttps() {
        assertThatThrownBy(() -> createProperties(
                VALID_JWT_SECRET,
                VALID_OTP_SECRET,
                Duration.ofMinutes(30),
                Duration.ofMinutes(5),
                5,
                "ftp://localhost:8080"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP or HTTPS");
    }

    private AuthSecurityProperties createProperties(
            String jwtSecret,
            String otpHmacSecret,
            Duration accessTokenTtl,
            Duration otpTtl,
            int otpMaxAttempts,
            String baseUrl
    ) {
        return new AuthSecurityProperties(
                jwtSecret,
                otpHmacSecret,
                "drss",
                accessTokenTtl,
                "DRSS_ACCESS_TOKEN",
                false,
                otpTtl,
                otpMaxAttempts,
                baseUrl,
                "test@example.invalid"
        );
    }
}