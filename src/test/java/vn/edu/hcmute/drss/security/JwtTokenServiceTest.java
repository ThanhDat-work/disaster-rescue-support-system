package vn.edu.hcmute.drss.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtTokenServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private JwtEncoder jwtEncoder;
    private JwtTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        jwtEncoder = mock(JwtEncoder.class);

        service = new JwtTokenService(
                jwtEncoder,
                properties(),
                Clock.fixed(
                        Instant.parse("2026-10-19T07:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        user = new User();
        user.setId(USER_ID);
        user.setAuthVersion(3);
    }

        @Test
    void issuesExactlyOneActiveRoleClaim() {
        Jwt encodedJwt = mock(Jwt.class);
        when(encodedJwt.getTokenValue()).thenReturn("signed.jwt.value");
        when(jwtEncoder.encode(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(encodedJwt);

        IssuedAccessToken result = service.issue(
                user,
                "ROLE_CITIZEN"
        );

        ArgumentCaptor<JwtEncoderParameters> captor =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());

        assertThat(captor.getValue().getClaims()
                .getClaimAsString("active_role"))
                .isEqualTo("ROLE_CITIZEN");
        assertThat(captor.getValue().getClaims()
                .getClaimAsStringList("roles"))
                .containsExactly("ROLE_CITIZEN");
        assertThat(result.tokenValue()).isEqualTo("signed.jwt.value");
    }

    @Test
    void refusesMultipleActiveRoles() {
        assertThatThrownBy(() -> service.issue(
                user,
                List.of("ROLE_CITIZEN", "ROLE_RESCUER")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Exactly one active role is required");
    }

    @Test
    void refusesToIssueTokenWithoutActiveRole() {
        assertThatThrownBy(() ->
                service.issue(user, List.of())
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Exactly one active role is required"
                );
    }

    private AuthSecurityProperties properties() {
        return new AuthSecurityProperties(
                "test-jwt-secret-at-least-32-bytes-aaaa",
                "test-otp-secret-at-least-32-bytes-bbbb",
                "test-rate-limit-secret-at-least-32-cccc",
                "drss",
                Duration.ofMinutes(30),
                "DRSS_ACCESS_TOKEN",
                false,
                Duration.ofMinutes(5),
                5,
                "http://localhost:8080",
                "noreply@drss.example"
        );
    }
}