package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.RoleSwitchAuditRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleSwitchServiceTest {

    private UserRepository users;
    private UserRoleRepository roles;
    private RoleSwitchAuditRepository audits;
    private PasswordEncoder passwords;
    private AuthRateLimitService limits;
    private JwtTokenService tokens;
    private RoleSwitchService service;
    private User user;
    private AuthenticatedUserPrincipal principal;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        roles = mock(UserRoleRepository.class);
        audits = mock(RoleSwitchAuditRepository.class);
        passwords = mock(PasswordEncoder.class);
        limits = mock(AuthRateLimitService.class);
        tokens = mock(JwtTokenService.class);
        service = new RoleSwitchService(
                users, roles, audits, passwords, limits, tokens
        );

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("multi@example.test");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");
        principal = new AuthenticatedUserPrincipal(
                user.getId(),
                user.getEmail(),
                "Multi Role",
                1,
                "ROLE_CITIZEN"
        );
        when(users.findByIdForUpdate(user.getId()))
                .thenReturn(Optional.of(user));
        when(limits.checkRoleSwitchAllowed(
                user.getEmail(), "203.0.113.9"
        )).thenReturn(RateLimitDecision.allowedDecision());
    }

    @Test
    void validPasswordAndGrantIssuesSingleRoleToken() {
        when(roles.existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                user, "ROLE_CITIZEN"
        )).thenReturn(true);
        when(roles.existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                user, "ROLE_COORDINATOR"
        )).thenReturn(true);
        when(passwords.matches("correct", "hash"))
                .thenReturn(true);
        IssuedAccessToken token = new IssuedAccessToken(
                "new.jwt",
                Instant.parse("2026-10-22T03:00:00Z")
        );
        when(tokens.issue(user, "ROLE_COORDINATOR"))
                .thenReturn(token);

        ActiveRoleChangeResult result = service.switchRole(
                principal,
                "ROLE_COORDINATOR",
                "correct",
                "203.0.113.9"
        );

        assertThat(result.status())
                .isEqualTo(ActiveRoleChangeResult.Status.SUCCESS);
        assertThat(result.redirectPath())
                .isEqualTo("/coordinator/pending");
        verify(audits).save(any());
    }

    @Test
    void forgedOrRevokedTargetNeverChecksPasswordOrIssuesToken() {
        when(roles.existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                user, "ROLE_CITIZEN"
        )).thenReturn(true);
        when(roles.existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                user, "ROLE_ADMIN"
        )).thenReturn(false);

        ActiveRoleChangeResult result = service.switchRole(
                principal,
                "ROLE_ADMIN",
                "correct",
                "203.0.113.9"
        );

        assertThat(result.status())
                .isEqualTo(ActiveRoleChangeResult.Status.INVALID);
        verify(passwords, never()).matches(any(), any());
        verify(tokens, never()).issue(any(User.class), any(String.class));
    }

    @Test
    void wrongPasswordRecordsFailureWithoutIssuingToken() {
        when(roles.existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                user, "ROLE_CITIZEN"
        )).thenReturn(true);
        when(roles.existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                user, "ROLE_COORDINATOR"
        )).thenReturn(true);
        when(passwords.matches("wrong", "hash"))
                .thenReturn(false);
        when(limits.recordRoleSwitchFailure(
                user.getEmail(), "203.0.113.9"
        )).thenReturn(RateLimitDecision.allowedDecision());

        ActiveRoleChangeResult result = service.switchRole(
                principal,
                "ROLE_COORDINATOR",
                "wrong",
                "203.0.113.9"
        );

        assertThat(result.status())
                .isEqualTo(ActiveRoleChangeResult.Status.INVALID);
        verify(audits).save(any());
        verify(tokens, never()).issue(any(User.class), any(String.class));
    }
}