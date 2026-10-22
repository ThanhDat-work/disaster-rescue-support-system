package vn.edu.hcmute.drss.controllers;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionAction;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestDetail;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestFilter;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.CoordinatorRequestCommandService;
import vn.edu.hcmute.drss.services.CoordinatorRequestQueryService;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;
import vn.edu.hcmute.drss.dtos.CoordinatorAssignmentRequest;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.entities.MediaAsset;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.services.CoordinatorAssignmentService;

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

    @MockitoBean
    private CoordinatorRequestCommandService commandService;

    @MockitoBean
    private CoordinatorAssignmentService assignmentService;

    @Test
    void coordinatorRoutesRequireAuthenticationAndCoordinatorRole()
            throws Exception {
        mockMvc.perform(get("/coordinator/pending"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));

        mockMvc.perform(get("/coordinator/pending")
                        .with(authenticatedAs("ROLE_CITIZEN")))
                .andExpect(status().isForbidden());

        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).with(authenticatedAs("ROLE_CITIZEN"))
                        .with(csrf())
                        .param("action", "VERIFY")
                        .param("version", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingQueueBindsFiltersAndRendersPage() throws Exception {
        DisasterType flood = disasterType();
        Page<RescueRequest> page = new PageImpl<>(List.of(request()));
        when(queryService.findPending(
                eq(USER_ID),
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
                eq(USER_ID),
                any(CoordinatorRequestFilter.class),
                eq(2)
        );
    }

    @Test
    void invalidFilterRedirectsWithSafeMessage() throws Exception {
        String message = "Bộ lọc không hợp lệ.";
        when(queryService.findPending(
                eq(USER_ID),
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
    void detailRendersDecisionFormWithCurrentVersion() throws Exception {
        RescueRequest request = request();
        CoordinatorRequestDetail detail = new CoordinatorRequestDetail(
                request,
                List.of(),
                List.of()
        );
        when(queryService.getDetail(request.getId(), USER_ID)).thenReturn(detail);

        mockMvc.perform(get(
                        "/coordinator/requests/{requestId}",
                        request.getId()
                ).with(authenticatedAs("ROLE_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/coordinator/request-detail"))
                .andExpect(model().attribute("detail", detail))
                .andExpect(model().attributeExists("decisionRequest"))
                .andExpect(model().attribute(
                        "decisionRequest",
                        org.hamcrest.Matchers.hasProperty(
                                "version",
                                org.hamcrest.Matchers.equalTo(4L))))
                .andExpect(model().attributeExists("assignmentRequest"))
                .andExpect(model().attribute(
                        "assignmentRequest",
                        org.hamcrest.Matchers.hasProperty(
                                "version",
                                org.hamcrest.Matchers.equalTo(4L)
                        )
                ))
                .andExpect(model().attribute(
                        "availableTeams",
                        List.of()
                ));
    }

    @Test
void detailRendersImageAndVideoUsingTheirResourceTypes()
        throws Exception {
    RescueRequest request = request();
    MediaAsset image = mediaAsset(
            MediaResourceType.IMAGE,
            "https://example.invalid/evidence.jpg",
            "image/jpeg",
            "jpg"
    );
    MediaAsset video = mediaAsset(
            MediaResourceType.VIDEO,
            "https://example.invalid/evidence.mp4",
            "video/mp4",
            "mp4"
    );
    CoordinatorRequestDetail detail = new CoordinatorRequestDetail(
            request,
            List.of(),
            List.of(image, video)
    );
    when(queryService.getDetail(request.getId(), USER_ID)).thenReturn(detail);

    String html = mockMvc.perform(get(
                    "/coordinator/requests/{requestId}",
                    request.getId()
            ).with(authenticatedAs("ROLE_COORDINATOR")))
            .andExpect(status().isOk())
            .andExpect(view().name(
                    "pages/coordinator/request-detail"
            ))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html)
            .containsPattern(
                    "(?s)<img[^>]+src=\"https://example\\.invalid/evidence\\.jpg\""
            )
            .containsPattern(
                    "(?s)<video[^>]*>.*<source[^>]+src=\"https://example\\.invalid/evidence\\.mp4\"[^>]+type=\"video/mp4\""
            )
            .doesNotContainPattern(
                    "(?s)<img[^>]+src=\"https://example\\.invalid/evidence\\.mp4\""
            );
}

    @Test
    void missingDetailReturns404() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(queryService.getDetail(requestId, USER_ID))
                .thenThrow(new CoordinatorRequestNotFoundException());

        mockMvc.perform(get(
                        "/coordinator/requests/{requestId}",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void decisionPostRequiresCsrf() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .param("action", "VERIFY")
                        .param("version", "4"))
                .andExpect(status().isForbidden());

        verify(commandService, never()).decide(any(), any(), any());
    }

    @Test
    void validVerifyUsesAuthenticatedCoordinatorAndVersion()
            throws Exception {
        UUID requestId = UUID.randomUUID();
        when(commandService.decide(
                eq(requestId),
                eq(USER_ID),
                any(CoordinatorDecisionRequest.class)
        )).thenReturn("VERIFIED");

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("action", "VERIFY")
                        .param("version", "4"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + requestId
                ))
                .andExpect(flash().attribute(
                        "successMsg",
                        "Đã xác minh yêu cầu cứu nạn."
                ));

        ArgumentCaptor<CoordinatorDecisionRequest> command =
                ArgumentCaptor.forClass(CoordinatorDecisionRequest.class);
        verify(commandService).decide(
                eq(requestId),
                eq(USER_ID),
                command.capture()
        );
        assertThat(command.getValue().getAction())
                .isEqualTo(CoordinatorDecisionAction.VERIFY);
        assertThat(command.getValue().getVersion()).isEqualTo(4L);
    }

    @Test
    void validRejectPassesReasonToService() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(commandService.decide(
                eq(requestId),
                eq(USER_ID),
                any(CoordinatorDecisionRequest.class)
        )).thenReturn("REJECTED");

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("action", "REJECT")
                        .param("version", "4")
                        .param("reason", "Thiếu thông tin vị trí"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + requestId
                ))
                .andExpect(flash().attribute(
                        "successMsg",
                        "Đã từ chối yêu cầu cứu nạn."
                ));

        ArgumentCaptor<CoordinatorDecisionRequest> command =
                ArgumentCaptor.forClass(CoordinatorDecisionRequest.class);
        verify(commandService).decide(
                eq(requestId),
                eq(USER_ID),
                command.capture()
        );
        assertThat(command.getValue().getAction())
                .isEqualTo(CoordinatorDecisionAction.REJECT);
        assertThat(command.getValue().getReason())
                .isEqualTo("Thiếu thông tin vị trí");
    }

    @Test
    void malformedDecisionIsRejectedBeforeService() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("version", "4"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + requestId
                ))
                .andExpect(flash().attributeExists("errorMsg"));

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("action", "REJECT")
                        .param("version", "4")
                        .param("reason", "x".repeat(2001)))
                .andExpect(status().isFound())
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Lý do từ chối tối đa 2.000 ký tự."
                ));

        verify(commandService, never()).decide(any(), any(), any());
    }

    @Test
    void serviceValidationAndConflictReturnSafeFlashMessage()
            throws Exception {
        UUID blankReasonId = UUID.randomUUID();
        when(commandService.decide(
                eq(blankReasonId),
                eq(USER_ID),
                any(CoordinatorDecisionRequest.class)
        )).thenThrow(new IllegalArgumentException(
                "Vui lòng nhập lý do từ chối."
        ));

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        blankReasonId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("action", "REJECT")
                        .param("version", "4")
                        .param("reason", "   "))
                .andExpect(status().isFound())
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Vui lòng nhập lý do từ chối."
                ));

        UUID conflictId = UUID.randomUUID();
        when(commandService.decide(
                eq(conflictId),
                eq(USER_ID),
                any(CoordinatorDecisionRequest.class)
        )).thenThrow(new CoordinatorRequestConflictException(
                "Yêu cầu vừa được cập nhật. Vui lòng tải lại trang."
        ));

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        conflictId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("action", "VERIFY")
                        .param("version", "3"))
                .andExpect(status().isFound())
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Yêu cầu vừa được cập nhật. Vui lòng tải lại trang."
                ));
    }

    @Test
    void optimisticFailureReturnsConcurrentUpdateMessage()
            throws Exception {
        UUID requestId = UUID.randomUUID();
        when(commandService.decide(
                eq(requestId),
                eq(USER_ID),
                any(CoordinatorDecisionRequest.class)
        )).thenThrow(new OptimisticLockingFailureException("stale"));

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("action", "VERIFY")
                        .param("version", "4"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + requestId
                ))
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Yêu cầu vừa được cập nhật. Vui lòng tải lại trang."
                ));
    }

    @Test
    void missingRequestDuringPostReturnsToQueue() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(commandService.decide(
                eq(requestId),
                eq(USER_ID),
                any(CoordinatorDecisionRequest.class)
        )).thenThrow(new CoordinatorRequestNotFoundException());

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("action", "VERIFY")
                        .param("version", "4"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/coordinator/pending"))
                .andExpect(flash().attributeExists("errorMsg"));
    }

        @Test
    void verifiedDetailProvidesOnlyAvailableAssignmentTeams()
            throws Exception {
        RescueRequest request = request();
        request.setStatus("VERIFIED");
        RescueTeam team = team();
        CoordinatorRequestDetail detail = new CoordinatorRequestDetail(
                request,
                List.of(),
                List.of()
        );

        when(queryService.getDetail(request.getId(), USER_ID)).thenReturn(detail);
        when(assignmentService.findAvailableTeams(request.getUser().getId()))
                .thenReturn(List.of(team));

        mockMvc.perform(get(
                        "/coordinator/requests/{requestId}",
                        request.getId()
                ).with(authenticatedAs("ROLE_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(model().attribute(
                        "availableTeams",
                        List.of(team)
                ))
                .andExpect(model().attribute(
                        "assignmentRequest",
                        org.hamcrest.Matchers.hasProperty(
                                "version",
                                org.hamcrest.Matchers.equalTo(4L)
                        )
                ));

        verify(assignmentService).findAvailableTeams(request.getUser().getId());
    }

    @Test
    void assignmentPostRequiresCoordinatorRoleAndCsrf()
            throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/assign",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .param("teamId", teamId.toString())
                        .param("version", "4"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/assign",
                        requestId
                ).with(authenticatedAs("ROLE_CITIZEN"))
                        .with(csrf())
                        .param("teamId", teamId.toString())
                        .param("version", "4"))
                .andExpect(status().isForbidden());

        verify(assignmentService, never()).assign(any(), any(), any());
    }

    @Test
    void validAssignmentUsesPrincipalTeamVersionAndNote()
            throws Exception {
        UUID requestId = UUID.randomUUID();
        RescueTeam team = team();
        RescueAssignment assignment = new RescueAssignment();
        assignment.setDisplayCode("ASN-0123456789ABCDEF");

        when(assignmentService.assign(
                eq(requestId),
                eq(USER_ID),
                any(CoordinatorAssignmentRequest.class)
        )).thenReturn(assignment);

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/assign",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("teamId", team.getId().toString())
                        .param("version", "4")
                        .param("coordinatorNote", "Ưu tiên đường chính"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + requestId
                ))
                .andExpect(flash().attribute(
                        "successMsg",
                        "Đã phân công nhiệm vụ ASN-0123456789ABCDEF cho đội cứu hộ."
                ));

        ArgumentCaptor<CoordinatorAssignmentRequest> command =
                ArgumentCaptor.forClass(
                        CoordinatorAssignmentRequest.class
                );
        verify(assignmentService).assign(
                eq(requestId),
                eq(USER_ID),
                command.capture()
        );
        assertThat(command.getValue().getTeamId())
                .isEqualTo(team.getId());
        assertThat(command.getValue().getVersion()).isEqualTo(4L);
        assertThat(command.getValue().getCoordinatorNote())
                .isEqualTo("Ưu tiên đường chính");
    }

    @Test
    void malformedAssignmentIsRejectedBeforeService()
            throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/assign",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("version", "4"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + requestId
                ))
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Vui lòng chọn đội cứu hộ."
                ));

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/assign",
                        requestId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("teamId", UUID.randomUUID().toString())
                        .param("version", "4")
                        .param("coordinatorNote", "x".repeat(2001)))
                .andExpect(status().isFound())
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Ghi chú phân công tối đa 2.000 ký tự."
                ));

        verify(assignmentService, never()).assign(any(), any(), any());
    }

    @Test
    void assignmentConflictAndMissingRequestUseSafeRedirects()
            throws Exception {
        UUID conflictId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        when(assignmentService.assign(
                eq(conflictId),
                eq(USER_ID),
                any(CoordinatorAssignmentRequest.class)
        )).thenThrow(new CoordinatorRequestConflictException(
                "Yêu cầu đã có phân công đang hoạt động."
        ));

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/assign",
                        conflictId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("teamId", teamId.toString())
                        .param("version", "4"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + conflictId
                ))
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Yêu cầu đã có phân công đang hoạt động."
                ));

        UUID missingId = UUID.randomUUID();
        when(assignmentService.assign(
                eq(missingId),
                eq(USER_ID),
                any(CoordinatorAssignmentRequest.class)
        )).thenThrow(new CoordinatorRequestNotFoundException());

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/assign",
                        missingId
                ).with(authenticatedAs("ROLE_COORDINATOR"))
                        .with(csrf())
                        .param("teamId", teamId.toString())
                        .param("version", "4"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/coordinator/pending"))
                .andExpect(flash().attributeExists("errorMsg"));
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

    private MediaAsset mediaAsset(
        MediaResourceType resourceType,
        String secureUrl,
        String mimeType,
        String format
) {
    MediaAsset asset = new MediaAsset();
    asset.setId(UUID.randomUUID());
    asset.setResourceType(resourceType);
    asset.setSecureUrl(secureUrl);
    asset.setMimeType(mimeType);
    asset.setFormat(format);
    return asset;
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
        request.setVersion(4L);
        return request;
    }

    private RescueTeam team() {
        RescueTeam team = new RescueTeam();
        team.setId(UUID.randomUUID());
        team.setDisplayCode("TEAM-001");
        team.setTeamName("Đội Phản Ứng Nhanh");
        team.setLeaderName("Tran Cuu Ho");
        team.setStatus("AVAILABLE");
        return team;
    }

    private DisasterType disasterType() {
        DisasterType type = new DisasterType();
        type.setId(7L);
        type.setDisasterName("Lũ lụt");
        type.setIsActive(true);
        return type;
    }
}