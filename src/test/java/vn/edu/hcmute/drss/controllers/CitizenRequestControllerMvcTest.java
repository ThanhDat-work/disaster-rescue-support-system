package vn.edu.hcmute.drss.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.CitizenRequestService;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class CitizenRequestControllerMvcTest {
    private static final UUID USER_ID = UUID.randomUUID();
    @Autowired MockMvc mockMvc;
    @MockitoBean CitizenRequestService service;

    @Test void citizenRouteRequiresAuthenticationAndCitizenRole() throws Exception {
        mockMvc.perform(get("/requests"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
        mockMvc.perform(get("/requests").with(authenticatedAs("ROLE_RESCUER")))
                .andExpect(status().isForbidden());
    }

    @Test void citizenCanOpenRequestList() throws Exception {
        when(service.list(eq(USER_ID), eq(0))).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/requests").with(authenticatedAs("ROLE_CITIZEN")))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/citizen/request-list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bạn chưa gửi yêu cầu nào")));
        verify(service).list(USER_ID, 0);
    }

    @Test void createPostRequiresCsrf() throws Exception {
        mockMvc.perform(post("/requests").with(authenticatedAs("ROLE_CITIZEN"))
                        .param("disasterTypeId", "7").param("urgency", "HIGH")
                        .param("address", "Demo"))
                .andExpect(status().isForbidden());
        verify(service, never()).create(eq(USER_ID), any());
    }

    @Test void validCitizenPostUsesPrincipalAndCsrf() throws Exception {
        RescueRequest created = new RescueRequest();
        created.setId(UUID.randomUUID()); created.setDisplayCode("REQ-0123456789ABCDEF");
        when(service.create(eq(USER_ID), any())).thenReturn(created);
        mockMvc.perform(post("/requests").with(authenticatedAs("ROLE_CITIZEN")).with(csrf())
                        .param("disasterTypeId", "7").param("urgency", "HIGH")
                        .param("address", "123 Demo").param("latitude", "10.1")
                        .param("longitude", "106.2").param("userId", UUID.randomUUID().toString())
                        .param("status", "CLOSED").param("displayCode", "FORGED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/requests/" + created.getId()));
        verify(service).create(eq(USER_ID), any());
    }

    private RequestPostProcessor authenticatedAs(String role) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                USER_ID, "citizen@demo.com", "Citizen Demo", 0, List.of(role));
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority(role))));
    }
}
