package vn.edu.hcmute.drss.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth")
public record AuthSecurityProperties(
        String jwtSecret,
        String otpHmacSecret,
        String issuer,
        Duration accessTokenTtl,
        String cookieName,
        boolean cookieSecure,
        Duration otpTtl,
        int otpMaxAttempts,
        String baseUrl,
        String mailFrom
) {

    private static final int MINIMUM_SECRET_BYTES = 32;

    public AuthSecurityProperties {
        jwtSecret = requireSecret(jwtSecret, "JWT secret");
        otpHmacSecret = requireSecret(otpHmacSecret, "OTP HMAC secret");

        if (jwtSecret.equals(otpHmacSecret)) {
            throw new IllegalArgumentException(
                    "JWT secret and OTP HMAC secret must be different"
            );
        }

        issuer = requireText(issuer, "JWT issuer");
        cookieName = requireText(cookieName, "JWT cookie name");
        baseUrl = requireHttpUrl(baseUrl);
        mailFrom = requireText(mailFrom, "Mail sender");

        accessTokenTtl = requirePositiveDuration(
                accessTokenTtl,
                "JWT access-token TTL"
        );

        otpTtl = requirePositiveDuration(otpTtl, "OTP TTL");

        if (otpMaxAttempts <= 0) {
            throw new IllegalArgumentException(
                    "OTP maximum attempts must be greater than zero"
            );
        }
    }

    private static String requireSecret(String value, String name) {
        String secret = requireText(value, name);

        int byteLength = secret.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    name + " must contain at least "
                            + MINIMUM_SECRET_BYTES
                            + " UTF-8 bytes"
            );
        }

        return secret;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }

        return value;
    }

    private static Duration requirePositiveDuration(
            Duration value,
            String name
    ) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    name + " must be greater than zero"
            );
        }

        return value;
    }

    private static String requireHttpUrl(String value) {
        String url = requireText(value, "Application base URL");

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();

            if (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException(
                        "Application base URL must use HTTP or HTTPS"
                );
            }

            return url;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Application base URL must be a valid HTTP or HTTPS URL",
                    exception
            );
        }
    }
}