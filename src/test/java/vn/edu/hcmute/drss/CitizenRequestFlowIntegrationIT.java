package vn.edu.hcmute.drss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
class CitizenRequestFlowIntegrationIT {

    private static final UUID ADMIN_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID CITIZEN_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID RESCUER_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444"
    );
    private static final UUID VERIFIED_REQUEST_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666"
    );
    private static final UUID ASSIGNED_REQUEST_ID = UUID.fromString(
            "88888888-8888-8888-8888-888888888888"
    );
    private static final UUID OTHER_REQUEST_ID = UUID.fromString(
            "12121212-1212-1212-1212-121212121212"
    );
    private static final String COOKIE_NAME = "DRSS_ACCESS_TOKEN";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RescueRequestRepository requestRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void realCitizenJwtAndCsrfCreateServerOwnedPendingRequest()
            throws Exception {
        Cookie cookie = cookie(CITIZEN_ID, "ROLE_CITIZEN");
        Long disasterTypeId = activeDisasterTypeId();

        mockMvc.perform(post("/requests")
                        .cookie(cookie)
                        .with(csrf())
                        .param("disasterTypeId", disasterTypeId.toString())
                        .param("urgency", "HIGH")
                        .param("address", "T2-02 integration address")
                        .param("description", "T2-02 integration request")
                        .param("userId", ADMIN_ID.toString())
                        .param("status", "CLOSED")
                        .param("displayCode", "FORGED"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("/requests/*"))
                .andExpect(flash().attributeExists("successMsg"));

        entityManager.flush();
        entityManager.clear();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT LOWER(HEX(user_id)) AS owner_id,
                       status,
                       display_code
                FROM rescue_requests
                WHERE address = 'T2-02 integration address'
                """
        );
        assertThat(row.get("owner_id"))
                .isEqualTo(CITIZEN_ID.toString().replace("-", ""));
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("display_code").toString())
                .matches("REQ-[A-F0-9]{16}");
    }

    @Test
    void ownerIsolationUsesSameFlowForMissingAndOtherOwner()
            throws Exception {
        insertAdminOwnedRequest();
        Cookie cookie = cookie(CITIZEN_ID, "ROLE_CITIZEN");
        UUID missingId = UUID.fromString(
                "13131313-1313-1313-1313-131313131313"
        );
        String message = "Kh\u00f4ng t\u00ecm th\u1ea5y y\u00eau c\u1ea7u.";

        mockMvc.perform(get("/requests/{id}", OTHER_REQUEST_ID)
                        .cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests"))
                .andExpect(flash().attribute("errorMsg", message));

        mockMvc.perform(get("/requests/{id}", missingId)
                        .cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests"))
                .andExpect(flash().attribute("errorMsg", message));

        mockMvc.perform(post("/requests/{id}/cancel", OTHER_REQUEST_ID)
                        .cookie(cookie)
                        .with(csrf())
                        .param("reason", "Forged cancellation"))
                .andExpect(status().isFound())
                .andExpect(flash().attribute("errorMsg", message));
        assertThat(requestStatus(OTHER_REQUEST_ID)).isEqualTo("PENDING");
    }

    @Test
    void verifiedRequestCancelsOnceWhileAssignedRequestIsRejected()
            throws Exception {
        Cookie cookie = cookie(CITIZEN_ID, "ROLE_CITIZEN");
        long verifiedBefore = historyCount(VERIFIED_REQUEST_ID);

        mockMvc.perform(post("/requests/{id}/cancel", VERIFIED_REQUEST_ID)
                        .cookie(cookie)
                        .with(csrf())
                        .param("reason", "Situation resolved"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("successMsg"));
        entityManager.flush();
        entityManager.clear();

        assertThat(requestStatus(VERIFIED_REQUEST_ID))
                .isEqualTo("CANCELLED");
        assertThat(historyCount(VERIFIED_REQUEST_ID))
                .isEqualTo(verifiedBefore + 1);
        assertThat(cancelHistoryCount(VERIFIED_REQUEST_ID)).isEqualTo(1);

        mockMvc.perform(post("/requests/{id}/cancel", VERIFIED_REQUEST_ID)
                        .cookie(cookie)
                        .with(csrf())
                        .param("reason", "Second cancellation"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("errorMsg"));
        entityManager.flush();
        entityManager.clear();
        assertThat(historyCount(VERIFIED_REQUEST_ID))
                .isEqualTo(verifiedBefore + 1);

        long assignedBefore = historyCount(ASSIGNED_REQUEST_ID);
        mockMvc.perform(post("/requests/{id}/cancel", ASSIGNED_REQUEST_ID)
                        .cookie(cookie)
                        .with(csrf())
                        .param("reason", "Too late"))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("errorMsg"));
        assertThat(requestStatus(ASSIGNED_REQUEST_ID)).isEqualTo("ASSIGNED");
        assertThat(historyCount(ASSIGNED_REQUEST_ID))
                .isEqualTo(assignedBefore);
    }

    @Test
    void csrfAndRoleAuthorizationAreEnforced() throws Exception {
        Cookie citizen = cookie(CITIZEN_ID, "ROLE_CITIZEN");
        Cookie rescuer = cookie(RESCUER_ID, "ROLE_RESCUER");

        mockMvc.perform(post(
                        "/requests/{id}/cancel", VERIFIED_REQUEST_ID
                ).cookie(citizen).param("reason", "No CSRF"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/requests").cookie(rescuer))
                .andExpect(status().isForbidden());
    }

    @Test
    void staleRequestVersionIsRejected() {
        RescueRequest stale = requestRepository.findById(
                VERIFIED_REQUEST_ID
        ).orElseThrow();
        int updated = jdbcTemplate.update(
                """
                UPDATE rescue_requests
                SET version = version + 1
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                VERIFIED_REQUEST_ID.toString()
        );
        assertThat(updated).isEqualTo(1);
        stale.setStatus("CANCELLED");
        assertThatThrownBy(entityManager::flush)
                .isInstanceOfAny(
                        OptimisticLockException.class,
                        ObjectOptimisticLockingFailureException.class
                );
    }

    private Cookie cookie(UUID userId, String role) {
        User user = userRepository.findById(userId).orElseThrow();
        IssuedAccessToken token = jwtTokenService.issue(user, List.of(role));
        return new Cookie(COOKIE_NAME, token.tokenValue());
    }

    private Long activeDisasterTypeId() {
        return jdbcTemplate.queryForObject(
                """
                SELECT id FROM disaster_types
                WHERE is_active = TRUE ORDER BY id LIMIT 1
                """,
                Long.class
        );
    }

    private void insertAdminOwnedRequest() {
        jdbcTemplate.update(
                """
                INSERT INTO rescue_requests (
                    id, display_code, user_id, disaster_type_id,
                    urgency, status, address, description,
                    created_at, updated_at, version
                ) VALUES (
                    UNHEX(REPLACE(?, '-', '')), 'REQ-OTHEROWNER01',
                    UNHEX(REPLACE(?, '-', '')), ?, 'LOW', 'PENDING',
                    'Other owner address', 'Owner isolation test',
                    NOW(), NOW(), 0
                )
                """,
                OTHER_REQUEST_ID.toString(),
                ADMIN_ID.toString(),
                activeDisasterTypeId()
        );
        entityManager.clear();
    }

    private String requestStatus(UUID requestId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status FROM rescue_requests
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                String.class,
                requestId.toString()
        );
    }

    private long historyCount(UUID requestId) {
        Long result = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM status_histories
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                """,
                Long.class,
                requestId.toString()
        );
        return result == null ? 0 : result;
    }

    private long cancelHistoryCount(UUID requestId) {
        Long result = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM status_histories
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                  AND new_status = 'CANCELLED'
                  AND changed_by_user_id = UNHEX(REPLACE(?, '-', ''))
                """,
                Long.class,
                requestId.toString(),
                CITIZEN_ID.toString()
        );
        return result == null ? 0 : result;
    }
}
