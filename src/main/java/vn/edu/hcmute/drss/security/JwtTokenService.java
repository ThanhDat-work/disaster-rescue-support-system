package vn.edu.hcmute.drss.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.User;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final AuthSecurityProperties properties;
    private final Clock clock;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            AuthSecurityProperties properties,
            Clock clock
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(
            User user,
            String activeRole
    ) {
        validateUser(user);
        String normalizedRole = normalizeRole(activeRole);

        Instant issuedAt = clock.instant()
                .truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(
                properties.accessTokenTtl()
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("active_role", normalizedRole)
                .claim("roles", List.of(normalizedRole))
                .claim("ver", user.getAuthVersion())
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        Jwt jwt = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        );

        return new IssuedAccessToken(
                jwt.getTokenValue(),
                expiresAt
        );
    }

    /**
     * Giữ overload để các helper cũ truyền List một phần tử vẫn compile.
     * Từ T3-09, list nhiều role luôn bị từ chối.
     */
    public IssuedAccessToken issue(
            User user,
            List<String> activeRoles
    ) {
        if (activeRoles == null || activeRoles.size() != 1) {
            throw new IllegalArgumentException(
                    "Exactly one active role is required"
            );
        }
        return issue(user, activeRoles.get(0));
    }

    private void validateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(
                    "A persisted user is required"
            );
        }
    }

    private String normalizeRole(String activeRole) {
        if (activeRole == null || activeRole.isBlank()) {
            throw new IllegalArgumentException(
                    "Active role is required"
            );
        }

        String value = activeRole.strip();
        if (!value.matches(
                "ROLE_(CITIZEN|COORDINATOR|RESCUER|ADMIN)"
        )) {
            throw new IllegalArgumentException(
                    "Active role is not supported"
            );
        }
        return value;
    }
}