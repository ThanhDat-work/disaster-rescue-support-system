package vn.edu.hcmute.drss.controllers;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
import vn.edu.hcmute.drss.dtos.CoordinatorCloseRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestDetail;
import vn.edu.hcmute.drss.dtos.CoordinatorTrackingFilter;
import vn.edu.hcmute.drss.dtos.CoordinatorTrackingItem;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.CoordinatorAssignmentService;
import vn.edu.hcmute.drss.services.CoordinatorCloseService;
import vn.edu.hcmute.drss.services.CoordinatorRequestCommandService;
import vn.edu.hcmute.drss.services.CoordinatorRequestQueryService;
import vn.edu.hcmute.drss.services.CoordinatorTrackingService;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class CoordinatorTrackingCloseControllerMvcTest {

    private static final UUID COORDINATOR_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoordinatorRequestQueryService queryService;
    @MockitoBean
    private CoordinatorRequestCommandService commandService;
    @MockitoBean
    private CoordinatorAssignmentService assignmentService;
    @MockitoBean
    private CoordinatorTrackingService trackingService;
    @MockitoBean
    private CoordinatorCloseService closeService;

    @Test
    void trackingAndCloseRequireCoordinatorRoleAndCsrf() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(get("/coordinator/tracking"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));

        mockMvc.perform(get("/coordinator/tracking")
                        .with(authenticatedAs("ROLE_CITIZEN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/close",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .param("version", "4")
                        .param("closeNote", "Hoàn tất cứu nạn an toàn."))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/close",
                        requestId
                ).with(authenticatedAs("ROLE_CITIZEN"))
                        .with(csrf())
                        .param("version", "4")
                        .param("closeNote", "Hoàn tất cứu nạn an toàn."))
                .andExpect(status().isForbidden());

        verify(closeService, never()).close(any(), any(), any());
    }

    @Test
    void trackingBindsFiltersAndRendersPage() throws Exception {
        RescueRequest request = request("RESCUED");
        RescueAssignment assignment = assignment(request);
        Page<CoordinatorTrackingItem> page = new PageImpl<>(
                List.of(new CoordinatorTrackingItem(request, assignment))
        );
        DisasterType disasterType = request.getDisasterType();
        RescueTeam team = assignment.getTeam();

        when(trackingService.findTracking(
                eq(COORDINATOR_ID),
                any(CoordinatorTrackingFilter.class),
                eq(2)
        )).thenReturn(page);
        when(trackingService.getActiveDisasterTypes())
                .thenReturn(List.of(disasterType));
        when(trackingService.getTeams()).thenReturn(List.of(team));

        mockMvc.perform(get("/coordinator/tracking")
                        .with(authenticatedAs("ROLE_COORDINATOR"))
                        .param("page", "2")
                        .param("status", "RESCUED")
                        .param("urgency", "HIGH")
                        .param("teamId", team.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/coordinator/tracking-list"))
                .andExpect(model().attribute("pageData", page))
                .andExpect(model().attribute("teams", List.of(team)))
                .andExpect(model().attributeExists("filter"));
    }

    @Test
    void rescuedDetailProvidesAssignmentAndVersionedCloseForm()
            throws Exception {
        RescueRequest request = request("RESCUED");
        RescueAssignment assignment = assignment(request);
        CoordinatorRequestDetail detail = new CoordinatorRequestDetail(
                request,
                List.of(),
                List.of()
        );

        when(queryService.getDetail(request.getId(), COORDINATOR_ID)).thenReturn(detail);
        when(trackingService.findLatestAssignment(request.getId()))
                .thenReturn(Optional.of(assignment));

        mockMvc.perform(get(
                        "/coordinator/requests/{requestId}",
                        request.getId()
                ).with(authenticatedAs("ROLE_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/coordinator/request-detail"))
                .andExpect(model().attribute(
                        "currentAssignment",
                        assignment
                ))
                .andExpect(model().attribute(
                        "closeRequest",
                        hasProperty("version", equalTo(4L))
                ));
    }

    @Test
    void validCloseUsesAuthenticatedCoordinatorAndRedirectsToDetail()
            throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/close",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("version", "4")
                        .param("closeNote", "Hoàn tất cứu nạn an toàn."))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + requestId
                ))
                .andExpect(flash().attribute(
                        "successMsg",
                        "Đã đóng yêu cầu cứu nạn."
                ));

        verify(closeService).close(
                eq(requestId),
                eq(COORDINATOR_ID),
                any(CoordinatorCloseRequest.class)
        );
    }

    @Test
    void invalidCloseNoteAndConflictReturnSafeFlash() throws Exception {
        UUID invalidId = UUID.randomUUID();
        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/close",
                        invalidId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("version", "4")
                        .param("closeNote", "ngắn"))
                .andExpect(status().isFound())
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Ghi chú đóng phải có từ 10 đến 500 ký tự."
                ));
        verify(closeService, never()).close(
                eq(invalidId),
                any(),
                any()
        );

        UUID conflictId = UUID.randomUUID();
        when(closeService.close(
                eq(conflictId),
                eq(COORDINATOR_ID),
                any(CoordinatorCloseRequest.class)
        )).thenThrow(new CoordinatorRequestConflictException(
                "Chỉ yêu cầu RESCUED mới được đóng."
        ));

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/close",
                        conflictId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("version", "4")
                        .param("closeNote", "Hoàn tất cứu nạn an toàn."))
                .andExpect(status().isFound())
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Chỉ yêu cầu RESCUED mới được đóng."
                ));
    }

    private RequestPostProcessor authenticatedAs(String role) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                COORDINATOR_ID,
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

    private RescueRequest request(String status) {
        User citizen = new User();
        citizen.setId(UUID.randomUUID());
        citizen.setFullName("Nguyen Van A");
        citizen.setPhoneNumber("0900000000");
        citizen.setEmail("citizen@example.com");

        DisasterType type = new DisasterType();
        type.setId(7L);
        type.setDisasterName("Lũ lụt");

        RescueRequest request = new RescueRequest();
        request.setId(UUID.randomUUID());
        request.setDisplayCode("REQ-T303-MVC");
        request.setUser(citizen);
        request.setDisasterType(type);
        request.setUrgency("HIGH");
        request.setStatus(status);
        request.setAddress("123 Demo Street");
        request.setCreatedAt(LocalDateTime.now());
        request.setVersion(4L);
        return request;
    }

    private RescueAssignment assignment(RescueRequest request) {
        RescueTeam team = new RescueTeam();
        team.setId(UUID.randomUUID());
        team.setDisplayCode("TEAM-001");
        team.setTeamName("Đội Phản Ứng Nhanh");

        RescueAssignment assignment = new RescueAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setDisplayCode("ASN-T303-MVC");
        assignment.setRequest(request);
        assignment.setTeam(team);
        assignment.setStatus("COMPLETED");
        assignment.setCompletionTime(LocalDateTime.now());
        assignment.setNotes("Đã đưa người dân đến nơi an toàn.");
        return assignment;
    }
}