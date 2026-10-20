package vn.edu.hcmute.drss;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;
import vn.edu.hcmute.drss.services.RescuerService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
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
        .MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
class RescuerFlowIntegrationIT {

    private static final UUID RESCUER_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    private static final UUID CITIZEN_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final UUID REQUEST_ID =
            UUID.fromString(
                    "88888888-8888-8888-8888-888888888888"
            );

    private static final UUID ASSIGNMENT_ID =
            UUID.fromString(
                    "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            );

    private static final String COOKIE_NAME =
            "DRSS_ACCESS_TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RescuerService rescuerService;

    @Autowired
    private RescueAssignmentRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void repositoryRestrictsAssignmentsByActiveMembership() {
        List<RescueAssignment> rescuerAssignments =
                assignmentRepository.findAssignmentsByUserId(
                        RESCUER_ID
                );

        assertThat(rescuerAssignments)
                .extracting(RescueAssignment::getId)
                .contains(ASSIGNMENT_ID);

        assertThat(
                assignmentRepository.findAssignmentsByUserId(
                        CITIZEN_ID
                )
        ).isEmpty();

        assertThat(
                assignmentRepository.findByIdAndUserId(
                        ASSIGNMENT_ID,
                        CITIZEN_ID
                )
        ).isEmpty();
    }

    @Test
    void realJwtCookieRendersRescuerTaskList()
            throws Exception {
        Cookie accessToken = rescuerCookie();

        mockMvc.perform(
                        get("/team/tasks")
                                .cookie(accessToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("ASN-0001")
                ))
                .andExpect(content().string(
                        containsString("REQ-0002")
                ))
                .andExpect(content().string(
                        containsString(
                                "Đội Phản Ứng Nhanh"
                        )
                ));
    }

    @Test
    void realJwtAndCsrfCompleteTheFullTransition()
            throws Exception {
        Cookie accessToken = rescuerCookie();
        long initialHistoryCount = historyCount();

        mockMvc.perform(
                        post(
                                "/team/tasks/{id}/start",
                                ASSIGNMENT_ID
                        )
                                .cookie(accessToken)
                                .with(csrf())
                                .param(
                                        "notes",
                                        "Đã tiếp cận hiện trường"
                                )
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/team/tasks/" + ASSIGNMENT_ID
                ))
                .andExpect(flash().attributeExists(
                        "successMsg"
                ));

        entityManager.flush();
        entityManager.clear();

        assertThat(requestStatus())
                .isEqualTo("IN_PROGRESS");

        assertThat(assignmentStatus())
                .isEqualTo("ACCEPTED");

        assertThat(historyCount())
                .isEqualTo(initialHistoryCount + 1);

        assertThat(transitionHistoryCount(
                "ASSIGNED",
                "IN_PROGRESS"
        )).isEqualTo(1);

        mockMvc.perform(
                        post(
                                "/team/tasks/{id}/complete",
                                ASSIGNMENT_ID
                        )
                                .cookie(accessToken)
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
                .andExpect(flash().attributeExists(
                        "successMsg"
                ));

        entityManager.flush();
        entityManager.clear();

        assertThat(requestStatus())
                .isEqualTo("RESCUED");

        assertThat(assignmentStatus())
                .isEqualTo("COMPLETED");

        assertThat(historyCount())
                .isEqualTo(initialHistoryCount + 2);

        assertThat(transitionHistoryCount(
                "IN_PROGRESS",
                "RESCUED"
        )).isEqualTo(1);

        assertThat(completionTimeCount())
                .isEqualTo(1);

        mockMvc.perform(
                        post(
                                "/team/tasks/{id}/complete",
                                ASSIGNMENT_ID
                        )
                                .cookie(accessToken)
                                .with(csrf())
                                .param(
                                        "notes",
                                        "Không được hoàn tất lần hai"
                                )
                )
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists(
                        "errorMsg"
                ));

        entityManager.flush();
        entityManager.clear();

        assertThat(historyCount())
                .isEqualTo(initialHistoryCount + 2);
    }

    @Test
    void rescuerWithoutMembershipCannotOpenTask()
            throws Exception {
        Cookie accessToken = rescuerCookie();

        int deletedMemberships = jdbcTemplate.update(
                """
                DELETE FROM rescue_team_members
                WHERE user_id = UNHEX(REPLACE(?, '-', ''))
                """,
                RESCUER_ID.toString()
        );

        assertThat(deletedMemberships).isEqualTo(1);
        entityManager.clear();

        mockMvc.perform(
                        get(
                                "/team/tasks/{id}",
                                ASSIGNMENT_ID
                        )
                                .cookie(accessToken)
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/team/tasks"
                ))
                .andExpect(flash().attributeExists(
                        "errorMsg"
                ));
    }

    @Test
    void revokedRoleInvalidatesPreviouslyIssuedJwt()
            throws Exception {
        Cookie accessToken = rescuerCookie();

        int updatedRoles = jdbcTemplate.update(
                """
                UPDATE user_roles ur
                JOIN roles role ON role.id = ur.role_id
                SET ur.revoked_at = NOW()
                WHERE ur.user_id =
                    UNHEX(REPLACE(?, '-', ''))
                  AND role.role_name = 'ROLE_RESCUER'
                  AND ur.revoked_at IS NULL
                """,
                RESCUER_ID.toString()
        );

        assertThat(updatedRoles).isEqualTo(1);
        entityManager.clear();

        mockMvc.perform(
                        get("/team/tasks")
                                .cookie(accessToken)
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/login"
                ));
    }

    @Test
    void disabledUserCannotReusePreviouslyIssuedJwt()
            throws Exception {
        Cookie accessToken = rescuerCookie();

        int updatedUsers = jdbcTemplate.update(
                """
                UPDATE users
                SET status = 'BANNED'
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                RESCUER_ID.toString()
        );

        assertThat(updatedUsers).isEqualTo(1);
        entityManager.clear();

        mockMvc.perform(
                        get("/team/tasks")
                                .cookie(accessToken)
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/login"
                ));
    }

    @Test
    void staleAssignmentVersionIsRejected() {
        RescueAssignment staleAssignment =
                assignmentRepository.findById(
                        ASSIGNMENT_ID
                ).orElseThrow();

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE rescue_assignments
                SET version = version + 1
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                ASSIGNMENT_ID.toString()
        );

        assertThat(updatedRows).isEqualTo(1);

        staleAssignment.setStatus("ACCEPTED");

        assertThatThrownBy(entityManager::flush)
                .isInstanceOfAny(
                        OptimisticLockException.class,
                        ObjectOptimisticLockingFailureException.class
                );
    }

    private Cookie rescuerCookie() {
        User rescuer = userRepository.findById(
                RESCUER_ID
        ).orElseThrow();

        IssuedAccessToken token = jwtTokenService.issue(
                rescuer,
                List.of("ROLE_RESCUER")
        );

        return new Cookie(
                COOKIE_NAME,
                token.tokenValue()
        );
    }

    private String requestStatus() {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM rescue_requests
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                String.class,
                REQUEST_ID.toString()
        );
    }

    private String assignmentStatus() {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM rescue_assignments
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                String.class,
                ASSIGNMENT_ID.toString()
        );
    }

    private long historyCount() {
        Long result = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM status_histories
                WHERE request_id =
                    UNHEX(REPLACE(?, '-', ''))
                """,
                Long.class,
                REQUEST_ID.toString()
        );

        return result == null ? 0 : result;
    }

    private long transitionHistoryCount(
            String oldStatus,
            String newStatus
    ) {
        Long result = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM status_histories
                WHERE request_id =
                    UNHEX(REPLACE(?, '-', ''))
                  AND changed_by_user_id =
                    UNHEX(REPLACE(?, '-', ''))
                  AND old_status = ?
                  AND new_status = ?
                """,
                Long.class,
                REQUEST_ID.toString(),
                RESCUER_ID.toString(),
                oldStatus,
                newStatus
        );

        return result == null ? 0 : result;
    }

    private long completionTimeCount() {
        Long result = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM rescue_assignments
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                  AND completion_time IS NOT NULL
                """,
                Long.class,
                ASSIGNMENT_ID.toString()
        );

        return result == null ? 0 : result;
    }
}