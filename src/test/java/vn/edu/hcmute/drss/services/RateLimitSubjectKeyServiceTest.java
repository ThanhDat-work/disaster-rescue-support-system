package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitSubjectKeyServiceTest {

    private final RateLimitSubjectKeyService subjectKeyService =
            new RateLimitSubjectKeyService(testProperties());

    @Test
    void emailIpKeyIsDeterministicAfterNormalization() {
        String firstKey = subjectKeyService.createEmailIpKey(
                AuthRateLimitAction.LOGIN,
                " Citizen@Example.COM ",
                "192.168.001.010"
        );

        String secondKey = subjectKeyService.createEmailIpKey(
                AuthRateLimitAction.LOGIN,
                "citizen@example.com",
                "192.168.1.10"
        );

        assertThat(firstKey)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(secondKey)
                .doesNotContain("citizen@example.com")
                .doesNotContain("192.168.1.10");
    }

    @Test
    void keyIsBoundToActionScopeAndSubject() {
        String loginEmailIpKey =
                subjectKeyService.createEmailIpKey(
                        AuthRateLimitAction.LOGIN,
                        "citizen@example.com",
                        "203.0.113.10"
                );

        String registerEmailIpKey =
                subjectKeyService.createEmailIpKey(
                        AuthRateLimitAction.REGISTER,
                        "citizen@example.com",
                        "203.0.113.10"
                );

        String loginIpKey = subjectKeyService.createIpKey(
                AuthRateLimitAction.LOGIN,
                "203.0.113.10"
        );

        String otherIpKey = subjectKeyService.createIpKey(
                AuthRateLimitAction.LOGIN,
                "203.0.113.11"
        );

        assertThat(registerEmailIpKey)
                .isNotEqualTo(loginEmailIpKey);

        assertThat(loginIpKey)
                .isNotEqualTo(loginEmailIpKey)
                .isNotEqualTo(otherIpKey);
    }

    @Test
    void equivalentIpv6FormsProduceSameKey() {
        String expandedKey = subjectKeyService.createIpKey(
                AuthRateLimitAction.LOGIN,
                "2001:db8:0:0:0:0:0:1"
        );

        String compressedKey = subjectKeyService.createIpKey(
                AuthRateLimitAction.LOGIN,
                "2001:db8::1"
        );

        assertThat(expandedKey)
                .isEqualTo(compressedKey)
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void differentEmailsProduceDifferentEmailIpKeys() {
        String firstKey = subjectKeyService.createEmailIpKey(
                AuthRateLimitAction.LOGIN,
                "first@example.com",
                "203.0.113.10"
        );

        String secondKey = subjectKeyService.createEmailIpKey(
                AuthRateLimitAction.LOGIN,
                "second@example.com",
                "203.0.113.10"
        );

        assertThat(firstKey).isNotEqualTo(secondKey);
    }

    @Test
    void invalidInputIsRejected() {
        assertThatThrownBy(() ->
                subjectKeyService.createIpKey(
                        null,
                        "203.0.113.10"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rate-limit action is required");

        assertThatThrownBy(() ->
                subjectKeyService.createEmailIpKey(
                        AuthRateLimitAction.LOGIN,
                        " ",
                        "203.0.113.10"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Email is required for EMAIL_IP rate limiting"
                );

        assertThatThrownBy(() ->
                subjectKeyService.createIpKey(
                        AuthRateLimitAction.LOGIN,
                        "example.com"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "IP address must be numeric IPv4 or IPv6"
                );

        assertThatThrownBy(() ->
                subjectKeyService.createIpKey(
                        AuthRateLimitAction.LOGIN,
                        "999.1.1.1"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "IP address must be numeric IPv4 or IPv6"
                );
    }

    private static AuthSecurityProperties testProperties() {
        return new AuthSecurityProperties(
                "j".repeat(32),
                "o".repeat(32),
                "r".repeat(32),
                "drss",
                Duration.ofMinutes(30),
                "DRSS_ACCESS_TOKEN",
                false,
                Duration.ofMinutes(5),
                5,
                "http://localhost:8080",
                "noreply@example.com"
        );
    }
}