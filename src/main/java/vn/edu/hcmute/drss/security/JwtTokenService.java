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
import java.util.Objects;
import java.util.TreeSet;
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
            List<String> activeRoles
    ) {
        validateUser(user);

        List<String> normalizedRoles =
                normalizeRoles(activeRoles);

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
                .claim("roles", normalizedRoles)
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

    private void validateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(
                    "A persisted user is required"
            );
        }
    }

    private List<String> normalizeRoles(
            List<String> activeRoles
    ) {
        if (activeRoles == null) {
            throw new IllegalArgumentException(
                    "Active roles are required"
            );
        }

        TreeSet<String> normalized = new TreeSet<>();

        for (String role : activeRoles) {
            if (role == null || role.isBlank()) {
                continue;
            }

            String value = role.strip();

            if (!value.startsWith("ROLE_")) {
                throw new IllegalArgumentException(
                        "Role must start with ROLE_"
                );
            }

            normalized.add(value);
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one active role is required"
            );
        }

        return List.copyOf(normalized);
    }
}