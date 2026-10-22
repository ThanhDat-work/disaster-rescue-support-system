package vn.edu.hcmute.drss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.hcmute.drss.dtos.CoordinatorAssignmentRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorCloseRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionAction;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionRequest;
import vn.edu.hcmute.drss.services.BusinessNotificationService;
import vn.edu.hcmute.drss.services.CoordinatorAssignmentService;
import vn.edu.hcmute.drss.services.CoordinatorCloseService;
import vn.edu.hcmute.drss.services.CoordinatorRequestCommandService;
import vn.edu.hcmute.drss.services.RescuerService;

@SpringBootTest
@ActiveProfiles("it")
class ReleaseRollbackIntegrationIT {

    private static final UUID COORDINATOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID CITIZEN_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID RESCUER_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444"
    );
    private static final UUID TEAM_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555"
    );

    @Autowired
    private CoordinatorRequestCommandService commandService;
    @Autowired
    private CoordinatorAssignmentService assignmentService;
    @Autowired
    private CoordinatorCloseService closeService;
    @Autowired
    private RescuerService rescuerService;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private BusinessNotificationService notifications;

    private final Set<UUID> requestIds = new HashSet<>();

    @BeforeEach
    void resetNotificationMock() {
        reset(notifications);
    }

    @AfterEach
    void cleanCreatedRows() {
        for (UUID requestId : requestIds) {
            jdbc.update("""
                    DELETE FROM notifications
                    WHERE reference_type = 'RESCUE_ASSIGNMENT'
                      AND reference_id IN (
                          SELECT id FROM rescue_assignments
                          WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                      )
                    """, requestId.toString());
            jdbc.update("""
                    DELETE FROM notifications
                    WHERE reference_id = UNHEX(REPLACE(?, '-', ''))
                    """, requestId.toString());
            jdbc.update("""
                    DELETE FROM status_histories
                    WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                    """, requestId.toString());
            jdbc.update("""
                    DELETE FROM rescue_assignments
                    WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                    """, requestId.toString());
            jdbc.update("""
                    DELETE FROM rescue_requests
                    WHERE id = UNHEX(REPLACE(?, '-', ''))
                    """, requestId.toString());
        }
        requestIds.clear();
        reset(notifications);
    }

    @Test
    void decisionRollsBackStatusAndHistoryWhenNotificationFails() {
        UUID requestId = insertRequest("PENDING");
        doThrow(new IllegalStateException("forced notification failure"))
                .when(notifications)
                .notifyRequestStatusChanged(
                        any(),
                        eq("PENDING"),
                        eq("VERIFIED")
                );

        assertThatThrownBy(() -> commandService.decide(
                requestId,
                COORDINATOR_ID,
                decisionCommand()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(status(requestId)).isEqualTo("PENDING");
        assertThat(historyCount(requestId)).isZero();
    }

    @Test
    void assignmentRollsBackRequestHistoryAndAssignmentOnFailure() {
        UUID requestId = insertRequest("VERIFIED");
        doThrow(new IllegalStateException("forced notification failure"))
                .when(notifications)
                .notifyAssignmentCreated(any());

        assertThatThrownBy(() -> assignmentService.assign(
                requestId,
                COORDINATOR_ID,
                assignmentCommand()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(status(requestId)).isEqualTo("VERIFIED");
        assertThat(historyCount(requestId)).isZero();
        assertThat(assignmentCount(requestId)).isZero();
    }

    @Test
    void rescuerTransitionRollsBackAssignmentRequestAndHistory() {
        UUID requestId = insertRequest("ASSIGNED");
        UUID assignmentId = insertAssignment(
                requestId,
                "ASSIGNED",
                null,
                null
        );
        doThrow(new IllegalStateException("forced notification failure"))
                .when(notifications)
                .notifyRequestStatusChanged(
                        any(),
                        eq("ASSIGNED"),
                        eq("IN_PROGRESS")
                );

        assertThatThrownBy(() -> rescuerService.startRescue(
                assignmentId,
                RESCUER_ID,
                "Bắt đầu tiếp cận hiện trường."
        )).isInstanceOf(IllegalStateException.class);

        assertThat(status(requestId)).isEqualTo("ASSIGNED");
        assertThat(assignmentStatus(assignmentId)).isEqualTo("ASSIGNED");
        assertThat(historyCount(requestId)).isZero();
    }

    @Test
    void closeRollsBackStatusAndHistoryWhenNotificationFails() {
        UUID requestId = insertRequest("RESCUED");
        insertAssignment(
                requestId,
                "COMPLETED",
                LocalDateTime.now(),
                "Đã hoàn thành cứu hộ và bàn giao an toàn."
        );
        doThrow(new IllegalStateException("forced notification failure"))
                .when(notifications)
                .notifyRequestStatusChanged(
                        any(),
                        eq("RESCUED"),
                        eq("CLOSED")
                );

        assertThatThrownBy(() -> closeService.close(
                requestId,
                COORDINATOR_ID,
                closeCommand()
        )).isInstanceOf(IllegalStateException.class);

        assertThat(status(requestId)).isEqualTo("RESCUED");
        assertThat(historyCount(requestId)).isZero();
    }

    private UUID insertRequest(String status) {
        UUID requestId = UUID.randomUUID();
        requestIds.add(requestId);
        jdbc.update("""
                INSERT INTO rescue_requests (
                    id, display_code, user_id, disaster_type_id,
                    urgency, status, address, description,
                    created_at, updated_at, version
                ) VALUES (
                    UNHEX(REPLACE(?, '-', '')),
                    ?,
                    UNHEX(REPLACE(?, '-', '')),
                    (SELECT id FROM disaster_types
                     WHERE disaster_code = 'DIS-001'),
                    'HIGH', ?, 'Release hardening address',
                    'Release rollback integration request',
                    NOW(6), NOW(6), 0
                )
                """,
                requestId.toString(),
                code("REQ-HARD-", requestId),
                CITIZEN_ID.toString(),
                status
        );
        return requestId;
    }

    private UUID insertAssignment(
            UUID requestId,
            String status,
            LocalDateTime completionTime,
            String notes
    ) {
        UUID assignmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rescue_assignments (
                    id, display_code, request_id, team_id,
                    status, assigned_at, completion_time, notes,
                    version, created_at, updated_at
                ) VALUES (
                    UNHEX(REPLACE(?, '-', '')),
                    ?,
                    UNHEX(REPLACE(?, '-', '')),
                    UNHEX(REPLACE(?, '-', '')),
                    ?, DATE_SUB(NOW(6), INTERVAL 10 MINUTE),
                    ?, ?, 0, NOW(6), NOW(6)
                )
                """,
                assignmentId.toString(),
                code("ASN-HARD-", assignmentId),
                requestId.toString(),
                TEAM_ID.toString(),
                status,
                completionTime,
                notes
        );
        return assignmentId;
    }

    private CoordinatorDecisionRequest decisionCommand() {
        CoordinatorDecisionRequest command =
                new CoordinatorDecisionRequest();
        command.setAction(CoordinatorDecisionAction.VERIFY);
        command.setVersion(0L);
        return command;
    }

    private CoordinatorAssignmentRequest assignmentCommand() {
        CoordinatorAssignmentRequest command =
                new CoordinatorAssignmentRequest();
        command.setTeamId(TEAM_ID);
        command.setVersion(0L);
        command.setCoordinatorNote("Release rollback test");
        return command;
    }

    private CoordinatorCloseRequest closeCommand() {
        CoordinatorCloseRequest command = new CoordinatorCloseRequest();
        command.setVersion(0L);
        command.setCloseNote(
                "Điều phối viên xác nhận hoàn tất cứu nạn."
        );
        return command;
    }

    private String status(UUID requestId) {
        return jdbc.queryForObject("""
                SELECT status FROM rescue_requests
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """, String.class, requestId.toString());
    }

    private String assignmentStatus(UUID assignmentId) {
        return jdbc.queryForObject("""
                SELECT status FROM rescue_assignments
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """, String.class, assignmentId.toString());
    }

    private long historyCount(UUID requestId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM status_histories
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                """, Long.class, requestId.toString());
    }

    private long assignmentCount(UUID requestId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM rescue_assignments
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                """, Long.class, requestId.toString());
    }

    private String code(String prefix, UUID id) {
        return prefix + id.toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }
}