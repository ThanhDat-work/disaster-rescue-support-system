package vn.edu.hcmute.drss.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtCookieAuthenticationFilterTest {

    private static final String COOKIE_NAME =
            "DRSS_ACCESS_TOKEN";

    @Mock
    private JwtAuthenticationService authenticationService;

    @Mock
    private AuthSecurityProperties properties;

    private JwtCookieAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        when(properties.cookieName())
                .thenReturn(COOKIE_NAME);

        filter = new JwtCookieAuthenticationFilter(
                authenticationService,
                properties
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validCookieInstallsAuthentication() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setRemoteAddr("127.0.0.1");
        request.setCookies(
                new Cookie(COOKIE_NAME, "signed-token")
        );

        Authentication authentication =
                authentication();

        when(authenticationService.authenticate(
                "signed-token"
        )).thenReturn(Optional.of(authentication));

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        Authentication installed =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertThat(installed).isSameAs(authentication);
        assertThat(installed.getDetails()).isNotNull();
    }

    @Test
    void missingCookieLeavesRequestAnonymous()
            throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();

        verifyNoInteractions(authenticationService);
    }

    @Test
    void invalidCookieLeavesRequestAnonymous()
            throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setCookies(
                new Cookie(COOKIE_NAME, "invalid-token")
        );

        when(authenticationService.authenticate(
                "invalid-token"
        )).thenReturn(Optional.empty());

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();

        verify(authenticationService)
                .authenticate("invalid-token");
    }

    @Test
    void existingAuthenticationIsNotOverwritten()
            throws Exception {
        Authentication existing =
                new UsernamePasswordAuthenticationToken(
                        "existing-user",
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_CITIZEN"
                                )
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(existing);

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setCookies(
                new Cookie(COOKIE_NAME, "another-token")
        );

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isSameAs(existing);

        verify(
                authenticationService,
                never()
        ).authenticate("another-token");
    }

    @Test
    void duplicateAccessTokenCookiesAreRejected()
            throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setCookies(
                new Cookie(COOKIE_NAME, "token-one"),
                new Cookie(COOKIE_NAME, "token-two")
        );

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        ).isNull();

        verifyNoInteractions(authenticationService);
    }

    private Authentication authentication() {
        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(
                        UUID.fromString(
                                "11111111-1111-1111-1111-111111111111"
                        ),
                        "citizen@example.com",
                        "Demo Citizen",
                        3,
                        List.of("ROLE_CITIZEN")
                );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_CITIZEN"
                        )
                )
        );
    }
}