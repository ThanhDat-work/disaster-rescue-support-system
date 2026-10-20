package vn.edu.hcmute.drss.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class JwtAuthenticationService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ROLES_CLAIM = "roles";
    private static final String AUTH_VERSION_CLAIM = "ver";

    private final JwtDecoder jwtDecoder;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public JwtAuthenticationService(
            JwtDecoder jwtDecoder,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.jwtDecoder = jwtDecoder;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Authentication> authenticate(
            String tokenValue
    ) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }

        Jwt jwt;

        try {
            jwt = jwtDecoder.decode(tokenValue);
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }

        Optional<JwtIdentity> identity = extractIdentity(jwt);

        if (identity.isEmpty()) {
            return Optional.empty();
        }

        JwtIdentity jwtIdentity = identity.get();

        Optional<User> userOptional = userRepository.findById(
                jwtIdentity.userId()
        );

        if (userOptional.isEmpty()) {
            return Optional.empty();
        }

        User user = userOptional.get();

        if (!ACTIVE_STATUS.equals(user.getStatus())) {
            return Optional.empty();
        }

        if (user.getAuthVersion() != jwtIdentity.authVersion()) {
            return Optional.empty();
        }

        Set<String> currentDatabaseRoles =
                loadCurrentDatabaseRoles(user);

        TreeSet<String> effectiveRoles =
                new TreeSet<>(jwtIdentity.roles());

        /*
         * Chỉ giữ role đồng thời:
         * - có trong JWT lúc đăng nhập;
         * - vẫn còn hiệu lực trong database ở request hiện tại.
         *
         * Role mới được cấp sau khi JWT phát hành không tự động xuất hiện.
         * Role đã bị thu hồi bị loại bỏ ngay.
         */
        effectiveRoles.retainAll(currentDatabaseRoles);

        if (effectiveRoles.isEmpty()) {
            return Optional.empty();
        }

        List<String> roleList = List.copyOf(effectiveRoles);

        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getAuthVersion(),
                        roleList
                );

        List<SimpleGrantedAuthority> authorities =
                roleList.stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

        return Optional.of(authentication);
    }

    private Optional<JwtIdentity> extractIdentity(Jwt jwt) {
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()
                || jwt.getId() == null
                || jwt.getId().isBlank()
                || jwt.getIssuedAt() == null
                || jwt.getExpiresAt() == null) {
            return Optional.empty();
        }

        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();

        if (!expiresAt.isAfter(issuedAt)) {
            return Optional.empty();
        }

        UUID userId;

        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        Object rawVersion = jwt.getClaims().get(
                AUTH_VERSION_CLAIM
        );

        if (!(rawVersion instanceof Number versionNumber)) {
            return Optional.empty();
        }

        long versionAsLong = versionNumber.longValue();

        if (versionAsLong < 0
                || versionAsLong > Integer.MAX_VALUE) {
            return Optional.empty();
        }

        Optional<Set<String>> roles = extractRoles(jwt);

        if (roles.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
                new JwtIdentity(
                        userId,
                        (int) versionAsLong,
                        roles.get()
                )
        );
    }

    private Optional<Set<String>> extractRoles(Jwt jwt) {
        Object rawRoles = jwt.getClaims().get(ROLES_CLAIM);

        if (!(rawRoles instanceof Collection<?> collection)) {
            return Optional.empty();
        }

        TreeSet<String> roles = new TreeSet<>();

        for (Object value : collection) {
            if (!(value instanceof String role)
                    || role.isBlank()) {
                return Optional.empty();
            }

            String normalizedRole = role.strip();

            if (!normalizedRole.startsWith("ROLE_")) {
                return Optional.empty();
            }

            roles.add(normalizedRole);
        }

        if (roles.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(Set.copyOf(roles));
    }

    private Set<String> loadCurrentDatabaseRoles(User user) {
        TreeSet<String> roles = new TreeSet<>();

        for (UserRole userRole :
                userRoleRepository
                        .findAllByUserAndRevokedAtIsNull(user)) {

            if (userRole.getRole() == null) {
                continue;
            }

            String roleName =
                    userRole.getRole().getRoleName();

            if (roleName != null
                    && !roleName.isBlank()
                    && roleName.startsWith("ROLE_")) {
                roles.add(roleName.strip());
            }
        }

        return Set.copyOf(roles);
    }

    private record JwtIdentity(
            UUID userId,
            int authVersion,
            Set<String> roles
    ) {
    }
}