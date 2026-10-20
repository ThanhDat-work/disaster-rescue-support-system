package vn.edu.hcmute.drss.configs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

@SpringBootTest
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class AuthenticationCryptoConfigTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private Clock clock;

    @Test
    void passwordEncoderUsesBcryptStrengthTen() {
        String rawPassword = "StrongPassword123";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertThat(encodedPassword).isNotEqualTo(rawPassword);
        assertThat(encodedPassword).contains("$10$");
        assertThat(passwordEncoder.matches(
                rawPassword,
                encodedPassword
        )).isTrue();
        assertThat(passwordEncoder.matches(
                "WrongPassword123",
                encodedPassword
        )).isFalse();
    }

    @Test
    void jwtEncoderAndDecoderRoundTripWithHs256() {
        Instant issuedAt = clock.instant()
                .truncatedTo(ChronoUnit.SECONDS);

        JwtClaimsSet claims = createClaims(
                "drss",
                issuedAt
        );

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        Jwt encoded = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        );

        Jwt decoded = jwtDecoder.decode(
                encoded.getTokenValue()
        );

        assertThat(decoded.getSubject()).isEqualTo("user-123");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("drss");
        assertThat(decoded.getClaimAsStringList("roles"))
                .containsExactly("ROLE_CITIZEN");
        assertThat(
                ((Number) decoded.getClaim("ver")).intValue()
        ).isZero();
        assertThat(decoded.getId()).isNotBlank();
    }

    @Test
    void jwtDecoderRejectsWrongIssuer() {
        Instant issuedAt = clock.instant()
                .truncatedTo(ChronoUnit.SECONDS);

        JwtClaimsSet claims = createClaims(
                "another-system",
                issuedAt
        );

        Jwt encoded = jwtEncoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256)
                                .type("JWT")
                                .build(),
                        claims
                )
        );

        assertThatThrownBy(() ->
                jwtDecoder.decode(encoded.getTokenValue())
        ).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void jwtDecoderRejectsExpiredToken() {
        Instant issuedAt = clock.instant()
                .minusSeconds(600)
                .truncatedTo(ChronoUnit.SECONDS);

        Jwt encoded = jwtEncoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256)
                                .type("JWT")
                                .build(),
                        createClaims("drss", issuedAt)
                )
        );

        assertThatThrownBy(() ->
                jwtDecoder.decode(encoded.getTokenValue())
        ).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void jwtDecoderRejectsTamperedSignature() {
        Instant issuedAt = clock.instant()
                .truncatedTo(ChronoUnit.SECONDS);

        Jwt encoded = jwtEncoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256)
                                .type("JWT")
                                .build(),
                        createClaims("drss", issuedAt)
                )
        );

        String[] parts = encoded.getTokenValue()
                .split("\\.");

        String signature = parts[2];
        char replacement = signature.charAt(0) == 'A'
                ? 'B'
                : 'A';

        String tamperedToken = parts[0]
                + "."
                + parts[1]
                + "."
                + replacement
                + signature.substring(1);

        assertThatThrownBy(() ->
                jwtDecoder.decode(tamperedToken)
        ).isInstanceOf(JwtException.class);
    }

    @Test
    void applicationClockMatchesJvmZoneForLocalDateTimePersistence() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.systemDefault());
    }

    private JwtClaimsSet createClaims(
            String issuer,
            Instant issuedAt
    ) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("user-123")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(300))
                .id(UUID.randomUUID().toString())
                .claim(
                        "roles",
                        List.of("ROLE_CITIZEN")
                )
                .claim("ver", 0)
                .build();
    }
}