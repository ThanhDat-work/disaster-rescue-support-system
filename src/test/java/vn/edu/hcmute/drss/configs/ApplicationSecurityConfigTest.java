package vn.edu.hcmute.drss.configs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.security.JwtAuthenticationService;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
@Import(
        ApplicationSecurityConfigTest
                .SecurityTestRoutesConfiguration.class
)
class ApplicationSecurityConfigTest {

    private static final String COOKIE_NAME =
            "DRSS_ACCESS_TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtAuthenticationService authenticationService;

    @Test
    void publicLoginRouteDoesNotRequireAuthentication()
            throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "action=\"/auth/login\""
                )));
    }

    @Test
    void protectedRouteRedirectsAnonymousUserToLogin()
            throws Exception {
        mockMvc.perform(get("/profile/test"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/login"
                ));
    }

    @Test
    void invalidJwtCookieDoesNotAuthenticateRequest()
            throws Exception {
        when(authenticationService.authenticate(
                "invalid-token"
        )).thenReturn(Optional.empty());

        mockMvc.perform(
                        get("/profile/test")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                COOKIE_NAME,
                                                "invalid-token"
                                        )
                                )
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/login"
                ));
    }

    @ParameterizedTest
    @CsvSource({
            "citizen-token, ROLE_CITIZEN, /requests/test",
            "coordinator-token, ROLE_COORDINATOR, /coordinator/test",
            "rescuer-token, ROLE_RESCUER, /team/test",
            "admin-token, ROLE_ADMIN, /admin/test"
    })
    void eachRoleCanAccessItsOwnRoute(
            String token,
            String role,
            String route
    ) throws Exception {
        allowToken(token, role);

        mockMvc.perform(
                        get(route)
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                COOKIE_NAME,
                                                token
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(content().string("protected"));
    }

    @Test
    void serverRejectsAuthenticatedUserWithWrongRole()
            throws Exception {
        allowToken(
                "citizen-token",
                "ROLE_CITIZEN"
        );

        mockMvc.perform(
                        get("/admin/test")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                COOKIE_NAME,
                                                "citizen-token"
                                        )
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDoesNotImplicitlyReceiveCoordinatorRole()
            throws Exception {
        allowToken(
                "admin-token",
                "ROLE_ADMIN"
        );

        mockMvc.perform(
                        get("/coordinator/test")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                COOKIE_NAME,
                                                "admin-token"
                                        )
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void anyAuthenticatedRoleCanAccessProfile()
            throws Exception {
        allowToken(
                "rescuer-token",
                "ROLE_RESCUER"
        );

        mockMvc.perform(
                        get("/profile/test")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                COOKIE_NAME,
                                                "rescuer-token"
                                        )
                                )
                )
                .andExpect(status().isOk());
    }

    @Test
    void logoutWithoutCsrfIsRejected()
            throws Exception {
        allowToken(
                "citizen-token",
                "ROLE_CITIZEN"
        );

        mockMvc.perform(
                        post("/auth/logout")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                COOKIE_NAME,
                                                "citizen-token"
                                        )
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCannotInvokeLogout()
            throws Exception {
        mockMvc.perform(
                        post("/auth/logout")
                                .with(csrf())
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/login"
                ));
    }

    @Test
    void authenticatedLogoutDeletesJwtCookie()
            throws Exception {
        allowToken(
                "citizen-token",
                "ROLE_CITIZEN"
        );

        mockMvc.perform(
                        post("/auth/logout")
                                .with(csrf())
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                COOKIE_NAME,
                                                "citizen-token"
                                        )
                                )
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/login?logout"
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString(
                                "DRSS_ACCESS_TOKEN="
                        )
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("Max-Age=0")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("HttpOnly")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("SameSite=Lax")
                ));
    }

    private void allowToken(
            String token,
            String... roles
    ) {
        List<String> roleList = Arrays.asList(roles);

        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(
                        UUID.fromString(
                                "11111111-1111-1111-1111-111111111111"
                        ),
                        "security-test@example.com",
                        "Security Test User",
                        3,
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

        when(authenticationService.authenticate(token))
                .thenReturn(Optional.of(authentication));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestRoutesConfiguration {

        @Bean
        SecurityTestRoutesController
                securityTestRoutesController() {
            return new SecurityTestRoutesController();
        }
    }

    @RestController
    static class SecurityTestRoutesController {

        @GetMapping({
                "/requests/test",
                "/coordinator/test",
                "/team/test",
                "/admin/test",
                "/profile/test"
        })
        String protectedRoute() {
            return "protected";
        }
    }
}