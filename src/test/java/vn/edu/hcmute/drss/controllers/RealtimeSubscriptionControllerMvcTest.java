package vn.edu.hcmute.drss.controllers;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.RealtimeSubscriptionService;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class RealtimeSubscriptionControllerMvcTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID TEAM_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RealtimeSubscriptionService service;

    @Test
    void anonymousRequestDoesNotExposeTeamIds() throws Exception {
        mvc.perform(get("/api/realtime/subscriptions"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void onlyAuthenticatedIdentityAndActiveRoleReachService() throws Exception {
        when(service.eligibleTeamIds(USER_ID, "ROLE_RESCUER"))
                .thenReturn(List.of(TEAM_ID));
        var principal = new AuthenticatedUserPrincipal(USER_ID,
                "rescuer@example.com", "Rescuer", 1, "ROLE_RESCUER");
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_RESCUER")));

        mvc.perform(get("/api/realtime/subscriptions")
                        .param("userId", UUID.randomUUID().toString())
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamIds[0]").value(TEAM_ID.toString()));

        verify(service).eligibleTeamIds(USER_ID, "ROLE_RESCUER");
    }
}
