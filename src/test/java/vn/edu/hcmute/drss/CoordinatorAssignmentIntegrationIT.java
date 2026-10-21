package vn.edu.hcmute.drss;

import static org.assertj.core.api.Assertions.assertThat;
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

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
class CoordinatorAssignmentIntegrationIT {

    private static final UUID COORDINATOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID VERIFIED_REQUEST_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666"
    );
    private static final UUID AVAILABLE_TEAM_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555"
    );
    private static final String COOKIE_NAME = "DRSS_ACCESS_TOKEN";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void verifiedDetailListsAvailableTeamWithActiveMember()
            throws Exception {
        mockMvc.perform(get(
                        "/coordinator/requests/{requestId}",
                        VERIFIED_REQUEST_ID
                ).cookie(coordinatorCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TEAM-001")))
                .andExpect(content().string(containsString(
                        "Đội Phản Ứng Nhanh"
                )))
                .andExpect(content().string(containsString(
                        "Xác nhận phân công"
                )));
    }

    @Test
    void realJwtAndCsrfAssignRequestAtomically()
            throws Exception {
        long initialHistory = historyCount();
        long initialAssignments = activeAssignmentCount();

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/assign",
                        VERIFIED_REQUEST_ID
                ).cookie(coordinatorCookie())
                        .with(csrf())
                        .param("teamId", AVAILABLE_TEAM_ID.toString())
                        .param("version", "0")
                        .param(
                                "coordinatorNote",
                                "Ưu tiên tiếp cận từ đường chính"
                        ))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + VERIFIED_REQUEST_ID
                ))
                .andExpect(flash().attributeExists("successMsg"));

        entityManager.flush();
        entityManager.clear();

        assertThat(requestStatus()).isEqualTo("ASSIGNED");
        assertThat(activeAssignmentCount())
                .isEqualTo(initialAssignments + 1);
        assertThat(historyCount()).isEqualTo(initialHistory + 1);
        assertThat(assignmentStatus()).isEqualTo("ASSIGNED");
        assertThat(assignmentCompletionTime()).isNull();
        assertThat(assignmentNotes()).isNull();
        assertThat(teamStatus()).isEqualTo("AVAILABLE");
        assertThat(latestHistoryNote())
                .contains("TEAM-001")
                .contains("Ưu tiên tiếp cận từ đường chính");
    }

    @Test
    void teamWithoutActiveMemberCannotBeAssigned()
            throws Exception {
        jdbcTemplate.update(
                """
                UPDATE rescue_team_members
                SET left_at = NOW(6)
                WHERE team_id = UNHEX(REPLACE(?, '-', ''))
                  AND left_at IS NULL
                """,
                AVAILABLE_TEAM_ID.toString()
        );

        long initialHistory = historyCount();
        long initialAssignments = activeAssignmentCount();

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/assign",
                        VERIFIED_REQUEST_ID
                ).cookie(coordinatorCookie())
                        .with(csrf())
                        .param("teamId", AVAILABLE_TEAM_ID.toString())
                        .param("version", "0"))
                .andExpect(status().isFound())
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Đội cứu hộ đã chọn không có thành viên hoạt động."
                ));

        entityManager.flush();
        entityManager.clear();

        assertThat(requestStatus()).isEqualTo("VERIFIED");
        assertThat(activeAssignmentCount()).isEqualTo(initialAssignments);
        assertThat(historyCount()).isEqualTo(initialHistory);
    }

    @Test
    void existingActiveAssignmentBlocksDuplicateWithoutPartialWrites()
            throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO rescue_assignments (
                    id,
                    display_code,
                    request_id,
                    team_id,
                    status,
                    assigned_at,
                    completion_time,
                    notes,
                    version,
                    created_at,
                    updated_at
                ) VALUES (
                    UNHEX(REPLACE(?, '-', '')),
                    'ASN-DOUBLE-TEST',
                    UNHEX(REPLACE(?, '-', '')),
                    UNHEX(REPLACE(?, '-', '')),
                    'ASSIGNED',
                    NOW(6),
                    NULL,
                    NULL,
                    0,
                    NOW(6),
                    NOW(6)
                )
                """,
                UUID.randomUUID().toString(),
                VERIFIED_REQUEST_ID.toString(),
                AVAILABLE_TEAM_ID.toString()
        );

        long initialHistory = historyCount();

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/assign",
                        VERIFIED_REQUEST_ID
                ).cookie(coordinatorCookie())
                        .with(csrf())
                        .param("teamId", AVAILABLE_TEAM_ID.toString())
                        .param("version", "0"))
                .andExpect(status().isFound())
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Yêu cầu đã có phân công đang hoạt động."
                ));

        entityManager.flush();
        entityManager.clear();

        assertThat(requestStatus()).isEqualTo("VERIFIED");
        assertThat(activeAssignmentCount()).isEqualTo(1);
        assertThat(historyCount()).isEqualTo(initialHistory);
    }

    private Cookie coordinatorCookie() {
        User coordinator = userRepository.findById(COORDINATOR_ID)
                .orElseThrow();

        IssuedAccessToken token = jwtTokenService.issue(
                coordinator,
                List.of("ROLE_COORDINATOR")
        );

        return new Cookie(COOKIE_NAME, token.tokenValue());
    }

    private String requestStatus() {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM rescue_requests
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                String.class,
                VERIFIED_REQUEST_ID.toString()
        );
    }

    private long activeAssignmentCount() {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM rescue_assignments
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                  AND status IN ('ASSIGNED', 'ACCEPTED')
                """,
                Long.class,
                VERIFIED_REQUEST_ID.toString()
        );
        return count == null ? 0L : count;
    }

    private long historyCount() {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM status_histories
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                """,
                Long.class,
                VERIFIED_REQUEST_ID.toString()
        );
        return count == null ? 0L : count;
    }

    private String assignmentStatus() {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM rescue_assignments
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                ORDER BY created_at DESC
                LIMIT 1
                """,
                String.class,
                VERIFIED_REQUEST_ID.toString()
        );
    }

    private java.time.LocalDateTime assignmentCompletionTime() {
        List<java.time.LocalDateTime> values = jdbcTemplate.query(
                """
                SELECT completion_time
                FROM rescue_assignments
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                ORDER BY created_at DESC
                LIMIT 1
                """,
                (resultSet, rowNumber) -> resultSet.getObject(
                        "completion_time",
                        java.time.LocalDateTime.class
                ),
                VERIFIED_REQUEST_ID.toString()
        );
        return values.get(0);
    }

    private String assignmentNotes() {
        List<String> values = jdbcTemplate.query(
                """
                SELECT notes
                FROM rescue_assignments
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                ORDER BY created_at DESC
                LIMIT 1
                """,
                (resultSet, rowNumber) -> resultSet.getString("notes"),
                VERIFIED_REQUEST_ID.toString()
        );
        return values.get(0);
    }

    private String teamStatus() {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM rescue_teams
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                String.class,
                AVAILABLE_TEAM_ID.toString()
        );
    }

    private String latestHistoryNote() {
        return jdbcTemplate.queryForObject(
                """
                SELECT notes
                FROM status_histories
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """,
                String.class,
                VERIFIED_REQUEST_ID.toString()
        );
    }
}