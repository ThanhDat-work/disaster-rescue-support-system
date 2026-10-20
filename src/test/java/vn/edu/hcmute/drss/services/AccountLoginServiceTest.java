package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.hcmute.drss.dtos.LoginRequest;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.Role;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountLoginServiceTest {

    private static final String EMAIL =
            "citizen@example.com";
    private static final String PASSWORD =
            "secure password 123";
    private static final String IP_ADDRESS =
            "203.0.113.10";

    private UserRepository userRepository;
    private UserRoleRepository userRoleRepository;
    private PasswordEncoder passwordEncoder;
    private AuthRateLimitService rateLimitService;
    private JwtTokenService jwtTokenService;
    private AccountLoginService service;
    private LoginRequest request;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        rateLimitService = mock(AuthRateLimitService.class);
        jwtTokenService = mock(JwtTokenService.class);

        service = new AccountLoginService(
                userRepository,
                userRoleRepository,
                passwordEncoder,
                rateLimitService,
                jwtTokenService
        );

        request = new LoginRequest(
                EMAIL,
                PASSWORD
        );

        user = new User();
        user.setId(UUID.fromString(
                "11111111-1111-1111-1111-111111111111"
        ));
        user.setEmail(EMAIL);
        user.setPasswordHash("stored-bcrypt-hash");
        user.setStatus("ACTIVE");
        user.setAuthVersion(0);

        when(rateLimitService.checkLoginAllowed(
                EMAIL,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());
    }

    @Test
    void validLoginIssuesJwtFromAllActiveRoles() {
        UserRole citizen = userRole("ROLE_CITIZEN");
        UserRole rescuer = userRole("ROLE_RESCUER");

        IssuedAccessToken accessToken =
                new IssuedAccessToken(
                        "signed.jwt.value",
                        Instant.parse(
                                "2026-10-19T07:30:00Z"
                        )
                );

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                PASSWORD,
                user.getPasswordHash()
        )).thenReturn(true);

        when(userRoleRepository
                .findAllByUserAndRevokedAtIsNull(user))
                .thenReturn(List.of(rescuer, citizen));

        when(jwtTokenService.issue(
                user,
                List.of("ROLE_CITIZEN", "ROLE_RESCUER")
        )).thenReturn(accessToken);

        LoginResult result =
                service.login(request, IP_ADDRESS);

        assertThat(result.status())
                .isEqualTo(
                        LoginResult.Status.AUTHENTICATED
                );
        assertThat(result.accessToken()).isSameAs(accessToken);
        assertThat(result.toString())
                .contains("<redacted>")
                .doesNotContain("signed.jwt.value");

        verify(rateLimitService)
                .clearLoginSubjectAfterSuccess(
                        EMAIL,
                        IP_ADDRESS
                );
    }

    @Test
    void missingEmailAndWrongPasswordReturnSameResult() {
        User wrongPasswordUser = user;

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(wrongPasswordUser)
                );

        when(passwordEncoder.matches(
                anyString(),
                anyString()
        )).thenReturn(false);

        when(rateLimitService.recordLoginFailure(
                EMAIL,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        LoginResult missingEmail =
                service.login(request, IP_ADDRESS);

        LoginResult wrongPassword =
                service.login(request, IP_ADDRESS);

        assertThat(missingEmail.status()).isEqualTo(
                LoginResult.Status.INVALID_CREDENTIALS
        );
        assertThat(wrongPassword.status()).isEqualTo(
                LoginResult.Status.INVALID_CREDENTIALS
        );

        verify(jwtTokenService, never())
                .issue(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    void correctPasswordMayRevealInactiveStatus() {
        user.setStatus("INACTIVE");

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                PASSWORD,
                user.getPasswordHash()
        )).thenReturn(true);

        LoginResult result =
                service.login(request, IP_ADDRESS);

        assertThat(result.status())
                .isEqualTo(LoginResult.Status.INACTIVE);

        verify(jwtTokenService, never())
                .issue(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    void correctPasswordMayRevealBannedStatus() {
        user.setStatus("BANNED");

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                PASSWORD,
                user.getPasswordHash()
        )).thenReturn(true);

        LoginResult result =
                service.login(request, IP_ADDRESS);

        assertThat(result.status())
                .isEqualTo(LoginResult.Status.BANNED);
    }

    @Test
    void rateLimitStopsLoginBeforeRepositoryLookup() {
        LocalDateTime blockedUntil =
                LocalDateTime.of(
                        2026,
                        10,
                        19,
                        8,
                        15
                );

        when(rateLimitService.checkLoginAllowed(
                EMAIL,
                IP_ADDRESS
        )).thenReturn(
                RateLimitDecision.blockedUntil(blockedUntil)
        );

        LoginResult result =
                service.login(request, IP_ADDRESS);

        assertThat(result.status())
                .isEqualTo(LoginResult.Status.RATE_LIMITED);
        assertThat(result.retryAfter())
                .isEqualTo(blockedUntil);

        verify(userRepository, never())
                .findByEmailIgnoreCase(anyString());
    }

    @Test
    void activeAccountWithoutRoleDoesNotReceiveJwt() {
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                PASSWORD,
                user.getPasswordHash()
        )).thenReturn(true);

        when(userRoleRepository
                .findAllByUserAndRevokedAtIsNull(user))
                .thenReturn(List.of());

        LoginResult result =
                service.login(request, IP_ADDRESS);

        assertThat(result.status())
                .isEqualTo(
                        LoginResult.Status.NO_ACTIVE_ROLES
                );

        verify(jwtTokenService, never())
                .issue(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    private UserRole userRole(String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);

        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);

        return userRole;
    }
}