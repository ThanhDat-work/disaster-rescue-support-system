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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestDetail;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestFilter;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;
import vn.edu.hcmute.drss.services.CoordinatorRequestQueryService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
class CoordinatorFlowIntegrationIT {

    private static final UUID COORDINATOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID CITIZEN_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID SEEDED_VERIFIED_REQUEST_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666"
    );
    private static final String COOKIE_NAME = "DRSS_ACCESS_TOKEN";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CoordinatorRequestQueryService queryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void realMySqlQueueFiltersOrdersAndPaginatesPendingRequests()
            throws Exception {
        List<UUID> requestIds = new ArrayList<>();
        List<String> displayCodes = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusHours(2);

        for (int index = 0; index < 11; index++) {
            UUID requestId = UUID.randomUUID();
            String displayCode = String.format(
                    "REQ-T203-%08d",
                    index
            );

            insertRequest(
                    requestId,
                    displayCode,
                    "PENDING",
                    "HIGH",
                    "T2-03 Queue Address " + index,
                    baseTime.plusMinutes(index)
            );
            requestIds.add(requestId);
            displayCodes.add(displayCode);
        }

        // Request khác trạng thái không được lẫn vào queue PENDING.
        insertRequest(
                UUID.randomUUID(),
                "REQ-T203-VERIFIED",
                "VERIFIED",
                "HIGH",
                "T2-03 Queue Address Verified",
                baseTime.minusMinutes(1)
        );

        CoordinatorRequestFilter filter = new CoordinatorRequestFilter();
        filter.setKeyword("T203");
        filter.setUrgency("HIGH");

        Page<RescueRequest> firstPage = queryService.findPending(filter, 0);
        Page<RescueRequest> secondPage = queryService.findPending(filter, 1);

        assertThat(firstPage.getTotalElements()).isEqualTo(11);
        assertThat(firstPage.getSize())
                .isEqualTo(CoordinatorRequestQueryService.PAGE_SIZE);
        assertThat(firstPage.getContent())
                .extracting(RescueRequest::getId)
                .containsExactlyElementsOf(requestIds.subList(0, 10));
        assertThat(secondPage.getContent())
                .extracting(RescueRequest::getId)
                .containsExactly(requestIds.get(10));

        mockMvc.perform(get("/coordinator/pending")
                        .cookie(coordinatorCookie())
                        .param("keyword", "T203")
                        .param("urgency", "HIGH")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString(displayCodes.get(0))
                ))
                .andExpect(content().string(
                        containsString(displayCodes.get(9))
                ));
    }

    @Test
    void realMySqlDetailContainsContactLocationMediaAndHistory()
            throws Exception {
        String mediaUrl = "https://example.com/t203-detail.jpg";
        insertMedia(
                SEEDED_VERIFIED_REQUEST_ID,
                mediaUrl
        );

        CoordinatorRequestDetail detail = queryService.getDetail(
                SEEDED_VERIFIED_REQUEST_ID
        );

        assertThat(detail.request().getUser().getEmail())
                .isEqualTo("dan@demo.com");
        assertThat(detail.request().getAddress())
                .isEqualTo("123 Đường Demo");
        assertThat(detail.request().getLatitude()).isNotNull();
        assertThat(detail.request().getLongitude()).isNotNull();
        assertThat(detail.media())
                .extracting(asset -> asset.getSecureUrl())
                .containsExactly(mediaUrl);
        assertThat(detail.history())
                .anySatisfy(history -> {
                    assertThat(history.getOldStatus()).isEqualTo("PENDING");
                    assertThat(history.getNewStatus()).isEqualTo("VERIFIED");
                });

        mockMvc.perform(get(
                        "/coordinator/requests/{requestId}",
                        SEEDED_VERIFIED_REQUEST_ID
                ).cookie(coordinatorCookie()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("REQ-0001")))
                .andExpect(content().string(containsString("dan@demo.com")))
                .andExpect(content().string(containsString(mediaUrl)))
                .andExpect(content().string(containsString("VERIFIED")));
    }

    @Test
    void realJwtAndCsrfVerifyPendingRequestWithOneHistory()
            throws Exception {
        UUID requestId = UUID.randomUUID();
        insertRequest(
                requestId,
                "REQ-T203-VERIFY",
                "PENDING",
                "CRITICAL",
                "T2-03 Verify Address",
                LocalDateTime.now().minusMinutes(5)
        );

        long initialHistory = historyCount(requestId);

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).cookie(coordinatorCookie())
                        .with(csrf())
                        .param("action", "VERIFY")
                        .param("version", "0"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + requestId
                ))
                .andExpect(flash().attribute(
                        "successMsg",
                        "Đã xác minh yêu cầu cứu nạn."
                ));

        entityManager.flush();
        entityManager.clear();

        assertThat(requestStatus(requestId)).isEqualTo("VERIFIED");
        assertThat(historyCount(requestId)).isEqualTo(initialHistory + 1);
        assertThat(transitionCount(
                requestId,
                "PENDING",
                "VERIFIED"
        )).isEqualTo(1);
        assertThat(activeAssignmentCount(requestId)).isZero();
    }

    @Test
    void realJwtAndCsrfRejectPendingRequestWithTrimmedReason()
            throws Exception {
        UUID requestId = UUID.randomUUID();
        insertRequest(
                requestId,
                "REQ-T203-REJECT",
                "PENDING",
                "MEDIUM",
                "T2-03 Reject Address",
                LocalDateTime.now().minusMinutes(4)
        );

        long initialHistory = historyCount(requestId);

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).cookie(coordinatorCookie())
                        .with(csrf())
                        .param("action", "REJECT")
                        .param("version", "0")
                        .param(
                                "reason",
                                "  Không xác định được vị trí an toàn  "
                        ))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + requestId
                ))
                .andExpect(flash().attribute(
                        "successMsg",
                        "Đã từ chối yêu cầu cứu nạn."
                ));

        entityManager.flush();
        entityManager.clear();

        assertThat(requestStatus(requestId)).isEqualTo("REJECTED");
        assertThat(historyCount(requestId)).isEqualTo(initialHistory + 1);
        assertThat(transitionCount(
                requestId,
                "PENDING",
                "REJECTED"
        )).isEqualTo(1);
        assertThat(latestHistoryNote(requestId))
                .isEqualTo("Không xác định được vị trí an toàn");
        assertThat(activeAssignmentCount(requestId)).isZero();
    }

    @Test
    void cancelledRaceRejectsStaleDecisionWithoutPartialHistory()
            throws Exception {
        UUID requestId = UUID.randomUUID();
        insertRequest(
                requestId,
                "REQ-T203-RACE",
                "PENDING",
                "HIGH",
                "T2-03 Race Address",
                LocalDateTime.now().minusMinutes(3)
        );

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE rescue_requests
                SET status = 'CANCELLED',
                    version = version + 1,
                    updated_at = NOW(6)
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                requestId.toString()
        );
        assertThat(updatedRows).isEqualTo(1);

        long initialHistory = historyCount(requestId);

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        requestId
                ).cookie(coordinatorCookie())
                        .with(csrf())
                        .param("action", "VERIFY")
                        .param("version", "0"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/coordinator/requests/" + requestId
                ))
                .andExpect(flash().attribute(
                        "errorMsg",
                        "Yêu cầu vừa được cập nhật. Vui lòng tải lại trang."
                ));

        entityManager.flush();
        entityManager.clear();

        assertThat(requestStatus(requestId)).isEqualTo("CANCELLED");
        assertThat(historyCount(requestId)).isEqualTo(initialHistory);
        assertThat(activeAssignmentCount(requestId)).isZero();
    }

    @Test
    void realSecurityRejectsAnonymousCitizenAndForgedCoordinatorAccess()
            throws Exception {
        mockMvc.perform(get("/coordinator/pending"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));

        mockMvc.perform(get("/coordinator/pending")
                        .cookie(citizenCookie()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/coordinator/pending")
                        .cookie(coordinatorCookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post(
                        "/coordinator/requests/{requestId}/decision",
                        SEEDED_VERIFIED_REQUEST_ID
                ).cookie(citizenCookie())
                        .with(csrf())
                        .param("action", "VERIFY")
                        .param("version", "0"))
                .andExpect(status().isForbidden());
    }

    private void insertRequest(
            UUID requestId,
            String displayCode,
            String status,
            String urgency,
            String address,
            LocalDateTime createdAt
    ) {
        int insertedRows = jdbcTemplate.update(
                """
                INSERT INTO rescue_requests (
                    id,
                    display_code,
                    user_id,
                    disaster_type_id,
                    urgency,
                    status,
                    address,
                    latitude,
                    longitude,
                    description,
                    created_at,
                    updated_at,
                    version
                ) VALUES (
                    UNHEX(REPLACE(?, '-', '')),
                    ?,
                    UNHEX(REPLACE(?, '-', '')),
                    (
                        SELECT id
                        FROM disaster_types
                        WHERE disaster_code = 'DIS-001'
                    ),
                    ?,
                    ?,
                    ?,
                    10.762622,
                    106.660172,
                    'T2-03 coordinator integration test',
                    ?,
                    ?,
                    0
                )
                """,
                requestId.toString(),
                displayCode,
                CITIZEN_ID.toString(),
                urgency,
                status,
                address,
                createdAt,
                createdAt
        );

        assertThat(insertedRows).isEqualTo(1);
    }

    private void insertMedia(UUID requestId, String secureUrl) {
        int insertedRows = jdbcTemplate.update(
                """
                INSERT INTO media_assets (
                    id,
                    request_id,
                    uploader_user_id,
                    public_id,
                    secure_url,
                    format,
                    width,
                    height,
                    bytes,
                    created_at,
                    deleted_at
                ) VALUES (
                    UNHEX(REPLACE(?, '-', '')),
                    UNHEX(REPLACE(?, '-', '')),
                    UNHEX(REPLACE(?, '-', '')),
                    ?,
                    ?,
                    'jpg',
                    800,
                    600,
                    1024,
                    NOW(6),
                    NULL
                )
                """,
                UUID.randomUUID().toString(),
                requestId.toString(),
                CITIZEN_ID.toString(),
                "t203/detail/" + UUID.randomUUID(),
                secureUrl
        );

        assertThat(insertedRows).isEqualTo(1);
    }

    private Cookie coordinatorCookie() {
        return cookieFor(COORDINATOR_ID, "ROLE_COORDINATOR");
    }

    private Cookie citizenCookie() {
        return cookieFor(CITIZEN_ID, "ROLE_CITIZEN");
    }

    private Cookie cookieFor(UUID userId, String role) {
        User user = userRepository.findById(userId).orElseThrow();
        IssuedAccessToken token = jwtTokenService.issue(
                user,
                List.of(role)
        );
        return new Cookie(COOKIE_NAME, token.tokenValue());
    }

    private String requestStatus(UUID requestId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM rescue_requests
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                String.class,
                requestId.toString()
        );
    }

    private long historyCount(UUID requestId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM status_histories
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                """,
                Long.class,
                requestId.toString()
        );
        return count == null ? 0L : count;
    }

    private long transitionCount(
            UUID requestId,
            String oldStatus,
            String newStatus
    ) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM status_histories
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                  AND old_status = ?
                  AND new_status = ?
                """,
                Long.class,
                requestId.toString(),
                oldStatus,
                newStatus
        );
        return count == null ? 0L : count;
    }

    private long activeAssignmentCount(UUID requestId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM rescue_assignments
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                  AND status IN ('ASSIGNED', 'ACCEPTED')
                """,
                Long.class,
                requestId.toString()
        );
        return count == null ? 0L : count;
    }

    private String latestHistoryNote(UUID requestId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT notes
                FROM status_histories
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """,
                String.class,
                requestId.toString()
        );
    }
}