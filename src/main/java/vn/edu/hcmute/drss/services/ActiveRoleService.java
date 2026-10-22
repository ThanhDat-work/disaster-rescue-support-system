package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.RoleSwitchAudit;
import vn.edu.hcmute.drss.entities.RoleSwitchAuditOutcome;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.repositories.RoleSwitchAuditRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ActiveRoleService {

    public static final String ROLE_CITIZEN = "ROLE_CITIZEN";
    public static final String ROLE_COORDINATOR = "ROLE_COORDINATOR";
    public static final String ROLE_RESCUER = "ROLE_RESCUER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private static final Set<String> ALLOWED_ROLES = Set.of(
            ROLE_CITIZEN,
            ROLE_COORDINATOR,
            ROLE_RESCUER,
            ROLE_ADMIN
    );

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleSwitchAuditRepository auditRepository;
    private final JwtTokenService tokenService;

    public ActiveRoleService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            RoleSwitchAuditRepository auditRepository,
            JwtTokenService tokenService
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditRepository = auditRepository;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public List<String> activeRoles(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy tài khoản."
                ));
        return loadActiveRoles(user);
    }

    @Transactional
    public ActiveRoleChangeResult selectInitialRole(
            UUID userId,
            String targetRole,
            String sourceIp
    ) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElse(null);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            return ActiveRoleChangeResult.invalid();
        }

        String normalizedTarget = normalizeRole(targetRole);
        List<String> grants = loadActiveRoles(user);
        if (normalizedTarget == null
                || !grants.contains(normalizedTarget)) {
            recordAudit(
                    user,
                    null,
                    normalizedTarget,
                    RoleSwitchAuditOutcome.FAILURE,
                    "INITIAL_ROLE_NOT_GRANTED",
                    sourceIp
            );
            return ActiveRoleChangeResult.invalid();
        }

        IssuedAccessToken token = tokenService.issue(
                user,
                normalizedTarget
        );
        recordAudit(
                user,
                null,
                normalizedTarget,
                RoleSwitchAuditOutcome.SUCCESS,
                "INITIAL_ROLE_SELECTED",
                sourceIp
        );

        return ActiveRoleChangeResult.success(
                token,
                normalizedTarget,
                landingPath(normalizedTarget)
        );
    }

    public static String landingPath(String role) {
        return switch (role) {
            case ROLE_CITIZEN -> "/requests";
            case ROLE_COORDINATOR -> "/coordinator/pending";
            case ROLE_RESCUER -> "/team/tasks";
            case ROLE_ADMIN -> "/admin/users";
            default -> "/";
        };
    }

    public static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String value = role.strip();
        return ALLOWED_ROLES.contains(value) ? value : null;
    }

    public static List<String> sortRoles(List<String> roles) {
        return roles.stream()
                .filter(ALLOWED_ROLES::contains)
                .distinct()
                .sorted(Comparator.comparingInt(
                        ActiveRoleService::roleOrder
                ))
                .toList();
    }

    private List<String> loadActiveRoles(User user) {
        return sortRoles(userRoleRepository
                .findAllByUserAndRevokedAtIsNull(user)
                .stream()
                .map(UserRole::getRole)
                .filter(role -> role != null)
                .map(role -> role.getRoleName())
                .filter(role -> role != null)
                .toList());
    }

    private void recordAudit(
            User user,
            String fromRole,
            String toRole,
            RoleSwitchAuditOutcome outcome,
            String reasonCode,
            String sourceIp
    ) {
        RoleSwitchAudit audit = new RoleSwitchAudit();
        audit.setUser(user);
        audit.setFromRole(fromRole);
        audit.setToRole(toRole);
        audit.setOutcome(outcome);
        audit.setReasonCode(reasonCode);
        audit.setSourceIp(normalizeIp(sourceIp));
        auditRepository.save(audit);
    }

    static String normalizeIp(String sourceIp) {
        if (sourceIp == null || sourceIp.isBlank()) {
            return "unknown";
        }
        String value = sourceIp.strip();
        return value.length() <= 45
                ? value
                : value.substring(0, 45);
    }

    private static int roleOrder(String role) {
        return switch (role) {
            case ROLE_CITIZEN -> 0;
            case ROLE_COORDINATOR -> 1;
            case ROLE_RESCUER -> 2;
            case ROLE_ADMIN -> 3;
            default -> 99;
        };
    }
}