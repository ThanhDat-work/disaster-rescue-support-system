package vn.edu.hcmute.drss.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestDetail;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestFilter;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.CoordinatorRequestQueryService;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class CoordinatorRequestControllerMvcTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoordinatorRequestQueryService queryService;

    @Test
    void coordinatorRoutesRequireAuthenticationAndCoordinatorRole()
            throws Exception {
        mockMvc.perform(get("/coordinator/pending"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));

        mockMvc.perform(get("/coordinator/pending")
                        .with(authenticatedAs("ROLE_CITIZEN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingQueueBindsFiltersAndRendersPage() throws Exception {
        DisasterType flood = disasterType();
        Page<RescueRequest> page = new PageImpl<>(List.of(request()));
        when(queryService.findPending(
                any(CoordinatorRequestFilter.class),
                eq(2)
        )).thenReturn(page);
        when(queryService.getActiveDisasterTypes())
                .thenReturn(List.of(flood));

        mockMvc.perform(get("/coordinator/pending")
                        .param("page", "2")
                        .param("keyword", "REQ-01")
                        .param("urgency", "HIGH")
                        .param("disasterTypeId", "7")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-21")
                        .with(authenticatedAs("ROLE_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/coordinator/pending-list"))
                .andExpect(model().attribute("pageData", page))
                .andExpect(model().attribute("disasterTypes", List.of(flood)))
                .andExpect(model().attributeExists("filter"));

        verify(queryService).findPending(
                any(CoordinatorRequestFilter.class),
                eq(2)
        );
    }

    @Test
    void invalidFilterRedirectsWithSafeMessage() throws Exception {
        String message = "B\u1ED9 l\u1ECDc kh\u00F4ng h\u1EE3p l\u1EC7.";
        when(queryService.findPending(
                any(CoordinatorRequestFilter.class),
                eq(0)
        )).thenThrow(new IllegalArgumentException(message));

        mockMvc.perform(get("/coordinator/pending")
                        .param("urgency", "FORGED")
                        .with(authenticatedAs("ROLE_COORDINATOR")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/coordinator/pending"))
                .andExpect(flash().attribute("errorMsg", message));
    }

    @Test
    void detailRendersContactLocationMediaAndHistoryModel()
            throws Exception {
        RescueRequest request = request();
        CoordinatorRequestDetail detail = new CoordinatorRequestDetail(
                request,
                List.of(),
                List.of()
        );
        when(queryService.getDetail(request.getId())).thenReturn(detail);

        mockMvc.perform(get(
                        "/coordinator/requests/{requestId}",
                        request.getId()
                ).with(authenticatedAs("ROLE_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/coordinator/request-detail"))
                .andExpect(model().attribute("detail", detail));
    }

    @Test
    void missingDetailReturns404() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(queryService.getDetail(requestId))
                .thenThrow(new CoordinatorRequestNotFoundException());

        mockMvc.perform(get(
                        "/coordinator/requests/{requestId}",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR")))
                .andExpect(status().isNotFound());
    }

    private RequestPostProcessor authenticatedAs(String role) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                USER_ID,
                "coordinator@demo.com",
                "Coordinator Demo",
                0,
                List.of(role)
        );
        return authentication(new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(role))
        ));
    }

    private RescueRequest request() {
        User citizen = new User();
        citizen.setId(UUID.randomUUID());
        citizen.setFullName("Nguyen Van A");
        citizen.setPhoneNumber("0900000000");
        citizen.setEmail("citizen@example.com");

        RescueRequest request = new RescueRequest();
        request.setId(UUID.randomUUID());
        request.setDisplayCode("REQ-0123456789ABCDEF");
        request.setUser(citizen);
        request.setDisasterType(disasterType());
        request.setUrgency("HIGH");
        request.setStatus("PENDING");
        request.setAddress("123 Demo Street");
        request.setCreatedAt(LocalDateTime.now());
        return request;
    }

    private DisasterType disasterType() {
        DisasterType type = new DisasterType();
        type.setId(7L);
        type.setDisasterName("L\u0169 l\u1EE5t");
        type.setIsActive(true);
        return type;
    }
}
