package vn.edu.hcmute.drss.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication
        .UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.RescuerService;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.mockito.Mockito.doThrow;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class RescuerControllerMvcTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    private static final UUID ASSIGNMENT_ID =
            UUID.fromString(
                    "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RescuerService rescuerService;

    @BeforeEach
    void resetMocks() {
        reset(rescuerService);
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/team/tasks"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));

        verify(rescuerService, never())
                .getAssignmentsForUser(USER_ID);
    }

    @Test
    void citizenCannotAccessRescuerRoutes() throws Exception {
        mockMvc.perform(
                        get("/team/tasks")
                                .with(authenticatedAs(
                                        "ROLE_CITIZEN"
                                ))
                )
                .andExpect(status().isForbidden());

        verify(rescuerService, never())
                .getAssignmentsForUser(USER_ID);
    }

    @Test
    void rescuerCanRenderEmptyTaskList() throws Exception {
        when(rescuerService.getAssignmentsForUser(USER_ID))
                .thenReturn(List.of());

        mockMvc.perform(
                        get("/team/tasks")
                                .with(authenticatedAs(
                                        "ROLE_RESCUER"
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "pages/rescuer/task-list"
                ))
                .andExpect(model().attributeExists(
                        "assignments"
                ))
                .andExpect(content().string(
                        containsString("Chưa có nhiệm vụ")
                ));

        verify(rescuerService)
                .getAssignmentsForUser(USER_ID);
    }

    @Test
    void rescuerCanRenderAssignedTaskDetail()
            throws Exception {
        RescueAssignment assignment =
                assignedTask();

        when(rescuerService.getAssignmentDetail(
                ASSIGNMENT_ID,
                USER_ID
        )).thenReturn(assignment);

        mockMvc.perform(
                        get("/team/tasks/{id}", ASSIGNMENT_ID)
                                .with(authenticatedAs(
                                        "ROLE_RESCUER"
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "pages/rescuer/task-detail"
                ))
                .andExpect(model().attribute(
                        "assignment",
                        assignment
                ))
                .andExpect(content().string(
                        containsString("ASN-0001")
                ))
                .andExpect(content().string(
                        containsString("Bắt đầu cứu hộ")
                ))
                .andExpect(content().string(
                        containsString(
                                "action=\"/team/tasks/"
                                        + ASSIGNMENT_ID
                                        + "/start\""
                        )
                ))
                .andExpect(content().string(
                        containsString("name=\"_csrf\"")
                ));
    }

    @Test
    void startWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(
                        post(
                                "/team/tasks/{id}/start",
                                ASSIGNMENT_ID
                        )
                                .with(authenticatedAs(
                                        "ROLE_RESCUER"
                                ))
                                .param(
                                        "notes",
                                        "Đang tiếp cận hiện trường"
                                )
                )
                .andExpect(status().isForbidden());

        verify(rescuerService, never())
                .startRescue(
                        ASSIGNMENT_ID,
                        USER_ID,
                        "Đang tiếp cận hiện trường"
                );
    }

    @Test
    void startWithCsrfInvokesServiceAndRedirects()
            throws Exception {
        mockMvc.perform(
                        post(
                                "/team/tasks/{id}/start",
                                ASSIGNMENT_ID
                        )
                                .with(authenticatedAs(
                                        "ROLE_RESCUER"
                                ))
                                .with(csrf())
                                .param(
                                        "notes",
                                        "Đang tiếp cận hiện trường"
                                )
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/team/tasks/" + ASSIGNMENT_ID
                ))
                .andExpect(flash().attribute(
                        "successMsg",
                        "Đã bắt đầu thực hiện nhiệm vụ cứu hộ."
                ));

        verify(rescuerService).startRescue(
                ASSIGNMENT_ID,
                USER_ID,
                "Đang tiếp cận hiện trường"
        );
    }

    @Test
    void completeWithCsrfInvokesServiceAndRedirects()
            throws Exception {
        mockMvc.perform(
                        post(
                                "/team/tasks/{id}/complete",
                                ASSIGNMENT_ID
                        )
                                .with(authenticatedAs(
                                        "ROLE_RESCUER"
                                ))
                                .with(csrf())
                                .param(
                                        "notes",
                                        "Đã đưa người dân đến nơi an toàn"
                                )
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/team/tasks/" + ASSIGNMENT_ID
                ))
                .andExpect(flash().attribute(
                        "successMsg",
                        "Đã hoàn tất nhiệm vụ cứu hộ."
                ));

        verify(rescuerService).completeRescue(
                ASSIGNMENT_ID,
                USER_ID,
                "Đã đưa người dân đến nơi an toàn"
        );


    }

    @Test
void completeWithBlankNotesIsRejectedBeforeService()
        throws Exception {
    mockMvc.perform(
                    post(
                            "/team/tasks/{id}/complete",
                            ASSIGNMENT_ID
                    )
                            .with(authenticatedAs(
                                    "ROLE_RESCUER"
                            ))
                            .with(csrf())
                            .param("notes", "   ")
            )
            .andExpect(status().isFound())
            .andExpect(redirectedUrl(
                    "/team/tasks/" + ASSIGNMENT_ID
            ))
            .andExpect(flash().attribute(
                    "errorMsg",
                    "Kết quả cứu hộ không được để trống."
            ));

    verify(rescuerService, never())
            .completeRescue(
                    ASSIGNMENT_ID,
                    USER_ID,
                    "   "
            );
}

@Test
void completeWithoutCsrfIsForbidden() throws Exception {
    mockMvc.perform(
                    post(
                            "/team/tasks/{id}/complete",
                            ASSIGNMENT_ID
                    )
                            .with(authenticatedAs(
                                    "ROLE_RESCUER"
                            ))
                            .param(
                                    "notes",
                                    "Đã hoàn tất cứu hộ"
                            )
            )
            .andExpect(status().isForbidden());

    verify(rescuerService, never())
            .completeRescue(
                    ASSIGNMENT_ID,
                    USER_ID,
                    "Đã hoàn tất cứu hộ"
            );
}

@Test
void optimisticConflictShowsSafeMessage()
        throws Exception {
    doThrow(
            new ObjectOptimisticLockingFailureException(
                    RescueAssignment.class,
                    ASSIGNMENT_ID
            )
    ).when(rescuerService).startRescue(
            ASSIGNMENT_ID,
            USER_ID,
            "Bắt đầu cứu hộ"
    );

    mockMvc.perform(
                    post(
                            "/team/tasks/{id}/start",
                            ASSIGNMENT_ID
                    )
                            .with(authenticatedAs(
                                    "ROLE_RESCUER"
                            ))
                            .with(csrf())
                            .param(
                                    "notes",
                                    "Bắt đầu cứu hộ"
                            )
            )
            .andExpect(status().isFound())
            .andExpect(redirectedUrl(
                    "/team/tasks/" + ASSIGNMENT_ID
            ))
            .andExpect(flash().attribute(
                    "errorMsg",
                    "Nhiệm vụ vừa được cập nhật bởi một thao tác khác. "
                            + "Vui lòng tải lại trang và kiểm tra trạng thái mới."
            ));
}

    private RequestPostProcessor authenticatedAs(
            String role
    ) {
        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(
                        USER_ID,
                        "cuuho@demo.com",
                        "Nhân viên cứu hộ Demo",
                        0,
                        List.of(role)
                );

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(role)
                        )
                );

        return authentication(authentication);
    }

    private RescueAssignment assignedTask() {
        RescueRequest request = new RescueRequest();
        request.setDisplayCode("REQ-0002");
        request.setStatus("ASSIGNED");
        request.setUrgency("HIGH");
        request.setAddress("Khu vực demo");
        request.setDescription(
                "Người dân cần được hỗ trợ di chuyển."
        );

        RescueTeam team = new RescueTeam();
        team.setDisplayCode("TEAM-001");
        team.setTeamName("Đội cứu hộ Demo");
        team.setLeaderName("Đội trưởng Demo");
        team.setContactNumber("0900000000");

        RescueAssignment assignment =
                new RescueAssignment();

        assignment.setId(ASSIGNMENT_ID);
        assignment.setDisplayCode("ASN-0001");
        assignment.setRequest(request);
        assignment.setTeam(team);
        assignment.setStatus("ASSIGNED");
        assignment.setAssignedAt(
                LocalDateTime.of(
                        2026,
                        10,
                        20,
                        10,
                        0
                )
        );

        return assignment;
    }
}