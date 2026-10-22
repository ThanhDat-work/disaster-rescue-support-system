package vn.edu.hcmute.drss.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.RoleSwitchAudit;
import vn.edu.hcmute.drss.entities.RoleSwitchAuditOutcome;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.RoleSwitchAuditRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;

@Service
public class RoleSwitchService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleSwitchAuditRepository auditRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimitService rateLimitService;
    private final JwtTokenService tokenService;

    public RoleSwitchService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleSwitchAuditRepository auditRepository,
            PasswordEncoder passwordEncoder,
            AuthRateLimitService rateLimitService,
            JwtTokenService tokenService
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
        this.tokenService = tokenService;
    }

    @Transactional
    public ActiveRoleChangeResult switchRole(
            AuthenticatedUserPrincipal principal,
            String targetRole,
            String password,
            String sourceIp
    ) {
        if (principal == null) {
            return ActiveRoleChangeResult.invalid();
        }

        User user = userRepository
                .findByIdForUpdate(principal.userId())
                .orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            return ActiveRoleChangeResult.invalid();
        }

        RateLimitDecision allowed =
                rateLimitService.checkRoleSwitchAllowed(
                        user.getEmail(),
                        sourceIp
                );
        if (!allowed.allowed()) {
            recordAudit(
                    user,
                    principal.activeRole(),
                    ActiveRoleService.normalizeRole(targetRole),
                    "RATE_LIMITED",
                    sourceIp
            );
            return ActiveRoleChangeResult.rateLimited(
                    allowed.blockedUntil()
            );
        }

        String normalizedTarget =
                ActiveRoleService.normalizeRole(targetRole);
        boolean targetGranted = normalizedTarget != null
                && userRoleRepository
                .existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                        user,
                        normalizedTarget
                );
        boolean currentGranted = userRoleRepository
                .existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                        user,
                        principal.activeRole()
                );

        if (!targetGranted
                || !currentGranted
                || normalizedTarget.equals(principal.activeRole())) {
            recordAudit(
                    user,
                    principal.activeRole(),
                    normalizedTarget,
                    "INVALID_TARGET",
                    sourceIp
            );
            return ActiveRoleChangeResult.invalid();
        }

        boolean passwordMatches = passwordEncoder.matches(
                password == null ? "" : password,
                user.getPasswordHash()
        );
        if (!passwordMatches) {
            RateLimitDecision afterFailure =
                    rateLimitService.recordRoleSwitchFailure(
                            user.getEmail(),
                            sourceIp
                    );
            recordAudit(
                    user,
                    principal.activeRole(),
                    normalizedTarget,
                    "INVALID_CONFIRMATION",
                    sourceIp
            );

            return afterFailure.allowed()
                    ? ActiveRoleChangeResult.invalid()
                    : ActiveRoleChangeResult.rateLimited(
                    afterFailure.blockedUntil()
            );
        }

        IssuedAccessToken token = tokenService.issue(
                user,
                normalizedTarget
        );
        rateLimitService.clearRoleSwitchSubjectAfterSuccess(
                user.getEmail(),
                sourceIp
        );
        recordAudit(
                user,
                principal.activeRole(),
                normalizedTarget,
                "ROLE_SWITCHED",
                sourceIp,
                RoleSwitchAuditOutcome.SUCCESS
        );

        return ActiveRoleChangeResult.success(
                token,
                normalizedTarget,
                ActiveRoleService.landingPath(normalizedTarget)
        );
    }

    private void recordAudit(
            User user,
            String fromRole,
            String toRole,
            String reasonCode,
            String sourceIp
    ) {
        recordAudit(
                user,
                fromRole,
                toRole,
                reasonCode,
                sourceIp,
                RoleSwitchAuditOutcome.FAILURE
        );
    }

    private void recordAudit(
            User user,
            String fromRole,
            String toRole,
            String reasonCode,
            String sourceIp,
            RoleSwitchAuditOutcome outcome
    ) {
        RoleSwitchAudit audit = new RoleSwitchAudit();
        audit.setUser(user);
        audit.setFromRole(fromRole);
        audit.setToRole(toRole);
        audit.setOutcome(outcome);
        audit.setReasonCode(reasonCode);
        audit.setSourceIp(ActiveRoleService.normalizeIp(sourceIp));
        auditRepository.save(audit);
    }
}