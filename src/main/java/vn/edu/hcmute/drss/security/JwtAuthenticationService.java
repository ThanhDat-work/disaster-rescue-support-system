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
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtAuthenticationService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ACTIVE_ROLE_CLAIM = "active_role";
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
    public Optional<Authentication> authenticate(String tokenValue) {
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
        if (!ACTIVE_STATUS.equals(user.getStatus())
                || user.getAuthVersion()
                != jwtIdentity.authVersion()) {
            return Optional.empty();
        }

        boolean stillGranted = userRoleRepository
                .existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                        user,
                        jwtIdentity.activeRole()
                );
        if (!stillGranted) {
            return Optional.empty();
        }

        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getAuthVersion(),
                        jwtIdentity.activeRole()
                );

        return Optional.of(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(
                                jwtIdentity.activeRole()
                        ))
                )
        );
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

        Object rawRole = jwt.getClaims().get(ACTIVE_ROLE_CLAIM);
        if (!(rawRole instanceof String role)
                || !role.matches(
                "ROLE_(CITIZEN|COORDINATOR|RESCUER|ADMIN)"
        )) {
            return Optional.empty();
        }

        return Optional.of(new JwtIdentity(
                userId,
                (int) versionAsLong,
                role
        ));
    }

    private record JwtIdentity(
            UUID userId,
            int authVersion,
            String activeRole
    ) {
    }
}