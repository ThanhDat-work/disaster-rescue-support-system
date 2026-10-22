package vn.edu.hcmute.drss.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.security.ClientIpAddressResolver;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtCookieService;
import vn.edu.hcmute.drss.services.ActiveRoleChangeResult;
import vn.edu.hcmute.drss.services.ActiveRoleService;
import vn.edu.hcmute.drss.services.InitialRoleSelectionSessionService;
import vn.edu.hcmute.drss.services.RoleSwitchService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActiveRoleControllerMvcTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private RoleSwitchService switches;
    private JwtCookieService cookies;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ActiveRoleService activeRoles = mock(ActiveRoleService.class);
        switches = mock(RoleSwitchService.class);
        InitialRoleSelectionSessionService sessions =
                mock(InitialRoleSelectionSessionService.class);
        cookies = mock(JwtCookieService.class);
        ClientIpAddressResolver ips =
                mock(ClientIpAddressResolver.class);
        when(ips.resolve(any())).thenReturn("203.0.113.90");

        mockMvc = MockMvcBuilders.standaloneSetup(
                new ActiveRoleController(
                        activeRoles,
                        switches,
                        sessions,
                        cookies,
                        ips
                )
        ).build();
    }

    @Test
    void successfulSwitchReplacesCookieAndRedirectsToRoleHome()
            throws Exception {
        IssuedAccessToken token = new IssuedAccessToken(
                "new.jwt",
                Instant.parse("2026-10-22T03:00:00Z")
        );
        when(switches.switchRole(
                any(),
                eq("ROLE_COORDINATOR"),
                eq("Correct pass 123"),
                eq("203.0.113.90")
        )).thenReturn(ActiveRoleChangeResult.success(
                token,
                "ROLE_COORDINATOR",
                "/coordinator/pending"
        ));
        when(cookies.createAccessTokenCookie(token)).thenReturn(
                ResponseCookie.from("DRSS_ACCESS_TOKEN", "new.jwt")
                        .httpOnly(true)
                        .path("/")
                        .sameSite("Lax")
                        .build()
        );

        mockMvc.perform(post("/account/role-switch")
                        .principal(authentication())
                        .param("targetRole", "ROLE_COORDINATOR")
                        .param("password", "Correct pass 123"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/coordinator/pending"))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("DRSS_ACCESS_TOKEN=new.jwt")
                ));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(
                        USER_ID,
                        "multi@example.test",
                        "Multi Role",
                        1,
                        "ROLE_CITIZEN"
                );
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CITIZEN"))
        );
    }
}