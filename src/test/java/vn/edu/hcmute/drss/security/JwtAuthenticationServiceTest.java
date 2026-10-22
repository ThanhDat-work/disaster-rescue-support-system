package vn.edu.hcmute.drss.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    private JwtAuthenticationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new JwtAuthenticationService(
                jwtDecoder,
                userRepository,
                userRoleRepository
        );

        user = new User();
        user.setId(USER_ID);
        user.setEmail("citizen@example.com");
        user.setFullName("Demo Citizen");
        user.setStatus("ACTIVE");
        user.setAuthVersion(3);
    }

    @Test
    void authenticatesActiveUserWithCurrentRole() {
        Jwt jwt = jwt(
                3,
                List.of("ROLE_COORDINATOR")
        );

        when(jwtDecoder.decode("valid-token"))
                .thenReturn(jwt);

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(userRoleRepository
                .existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                        user,
                        "ROLE_COORDINATOR"
                ))
                .thenReturn(true);

        Optional<Authentication> result =
                service.authenticate("valid-token");

        assertThat(result).isPresent();

        Authentication authentication = result.orElseThrow();

        assertThat(authentication.isAuthenticated()).isTrue();

        assertThat(authentication.getPrincipal())
                .isInstanceOf(
                        AuthenticatedUserPrincipal.class
                );

        AuthenticatedUserPrincipal principal =
                (AuthenticatedUserPrincipal)
                        authentication.getPrincipal();

        assertThat(principal.userId())
                .isEqualTo(USER_ID);

        assertThat(principal.authVersion())
                .isEqualTo(3);

        assertThat(principal.activeRole())
                .isEqualTo("ROLE_COORDINATOR");

        assertThat(principal.roles())
                .containsExactly("ROLE_COORDINATOR");

        assertThat(authentication.getAuthorities())
                .extracting(authority ->
                        authority.getAuthority()
                )
                .containsExactly("ROLE_COORDINATOR");
    }

    @Test
    void invalidSignatureIsRejectedBeforeDatabaseLookup() {
        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new BadJwtException(
                        "Invalid token"
                ));

        assertThat(service.authenticate("invalid-token"))
                .isEmpty();

        verifyNoInteractions(
                userRepository,
                userRoleRepository
        );
    }

    @Test
    void inactiveUserIsRejected() {
        user.setStatus("INACTIVE");

        when(jwtDecoder.decode("valid-token"))
                .thenReturn(jwt(
                        3,
                        List.of("ROLE_CITIZEN")
                ));

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        assertThat(service.authenticate("valid-token"))
                .isEmpty();

        verifyNoInteractions(userRoleRepository);
    }

    @Test
    void oldAuthenticationVersionIsRejected() {
        when(jwtDecoder.decode("old-token"))
                .thenReturn(jwt(
                        2,
                        List.of("ROLE_CITIZEN")
                ));

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        assertThat(service.authenticate("old-token"))
                .isEmpty();

        verifyNoInteractions(userRoleRepository);
    }

    @Test
    void revokedActiveRoleRejectsWholeJwt() {
        when(jwtDecoder.decode("revoked-token"))
                .thenReturn(jwt(
                        3,
                        List.of("ROLE_ADMIN")
                ));

        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(user));

        when(userRoleRepository
                .existsByUserAndRole_RoleNameAndRevokedAtIsNull(
                        user,
                        "ROLE_ADMIN"
                ))
                .thenReturn(false);

        assertThat(service.authenticate("revoked-token"))
                .isEmpty();
    }

    @Test
    void malformedSubjectOrClaimsAreRejected() {
        Jwt malformed = Jwt.withTokenValue(
                        "malformed-token"
                )
                .header("alg", "HS256")
                .subject("not-a-uuid")
                .issuedAt(Instant.parse(
                        "2026-10-19T00:00:00Z"
                ))
                .expiresAt(Instant.parse(
                        "2026-10-19T00:30:00Z"
                ))
                .claim("jti", "jwt-id")
                .claim(
                        "roles",
                        List.of("ROLE_CITIZEN")
                )
                .claim("ver", 3)
                .build();

        when(jwtDecoder.decode("malformed-token"))
                .thenReturn(malformed);

        assertThat(service.authenticate("malformed-token"))
                .isEmpty();

        verifyNoInteractions(
                userRepository,
                userRoleRepository
        );
    }

    private Jwt jwt(
            int authVersion,
            List<String> roles
    ) {
        return Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .subject(USER_ID.toString())
                .issuedAt(Instant.parse(
                        "2026-10-19T00:00:00Z"
                ))
                .expiresAt(Instant.parse(
                        "2026-10-19T00:30:00Z"
                ))
                .claim("jti", "jwt-id")
                .claim(
                        "active_role",
                        roles.get(0)
                )
                .claim(
                        "roles",
                        List.of(roles.get(0))
                )
                .claim("ver", authVersion)
                .build();
    }
}