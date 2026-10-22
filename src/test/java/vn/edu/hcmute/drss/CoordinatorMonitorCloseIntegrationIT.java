package vn.edu.hcmute.drss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.CoordinatorCloseRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorTrackingFilter;
import vn.edu.hcmute.drss.dtos.CoordinatorTrackingItem;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.services.CoordinatorCloseService;
import vn.edu.hcmute.drss.services.CoordinatorTrackingService;

@SpringBootTest
@ActiveProfiles("it")
@Transactional
class CoordinatorMonitorCloseIntegrationIT {

    private static final UUID COORDINATOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID CITIZEN_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID TEAM_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555"
    );

    @Autowired
    private CoordinatorCloseService closeService;
    @Autowired
    private CoordinatorTrackingService trackingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void closePersistsClosedHistoryAndOwnerNotificationAtomically() {
        UUID requestId = UUID.randomUUID();
        insertRequest(requestId, "RESCUED", 0L);
        insertAssignment(
                requestId,
                "COMPLETED",
                LocalDateTime.now(),
                "Đã hoàn tất cứu nạn và bàn giao an toàn."
        );

        RescueRequest closed = closeService.close(
                requestId,
                COORDINATOR_ID,
                command(0L, "Điều phối viên xác nhận hoàn tất cứu nạn.")
        );

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(requestStatus(requestId)).isEqualTo("CLOSED");
        assertThat(transitionCount(requestId, "RESCUED", "CLOSED"))
                .isEqualTo(1);
        assertThat(closedNotificationCount(requestId)).isEqualTo(1);

        assertThatThrownBy(() -> closeService.close(
                requestId,
                COORDINATOR_ID,
                command(
                        closed.getVersion(),
                        "Thử đóng lại yêu cầu đã hoàn tất."
                )
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("RESCUED");

        assertThat(transitionCount(requestId, "RESCUED", "CLOSED"))
                .isEqualTo(1);
        assertThat(closedNotificationCount(requestId)).isEqualTo(1);
    }

    @Test
    void staleVersionAndIncompleteReportLeaveRequestUnchanged() {
        UUID staleRequestId = UUID.randomUUID();
        insertRequest(staleRequestId, "RESCUED", 0L);
        insertAssignment(
                staleRequestId,
                "COMPLETED",
                LocalDateTime.now(),
                "Báo cáo hoàn thành hợp lệ."
        );

        assertThatThrownBy(() -> closeService.close(
                staleRequestId,
                COORDINATOR_ID,
                command(99L, "Điều phối viên xác nhận hoàn tất cứu nạn.")
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("vừa được cập nhật");
        assertThat(requestStatus(staleRequestId)).isEqualTo("RESCUED");
        assertThat(closedNotificationCount(staleRequestId)).isZero();

        UUID noReportRequestId = UUID.randomUUID();
        insertRequest(noReportRequestId, "RESCUED", 0L);
        insertAssignment(
                noReportRequestId,
                "COMPLETED",
                LocalDateTime.now(),
                "   "
        );

        assertThatThrownBy(() -> closeService.close(
                noReportRequestId,
                COORDINATOR_ID,
                command(0L, "Điều phối viên xác nhận hoàn tất cứu nạn.")
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("báo cáo hoàn thành");
        assertThat(requestStatus(noReportRequestId)).isEqualTo("RESCUED");
        assertThat(transitionCount(
                noReportRequestId,
                "RESCUED",
                "CLOSED"
        )).isZero();
    }

    @Test
    void trackingFiltersByStatusAndTeamAndReturnsLatestAssignment() {
        UUID requestId = UUID.randomUUID();
        insertRequest(requestId, "IN_PROGRESS", 0L);
        insertAssignment(
                requestId,
                "ACCEPTED",
                null,
                "Đội đang tiếp cận hiện trường."
        );

        CoordinatorTrackingFilter filter = new CoordinatorTrackingFilter();
        filter.setStatus("IN_PROGRESS");
        filter.setTeamId(TEAM_ID);

        Page<CoordinatorTrackingItem> page =
                trackingService.findTracking(filter, 0);

        assertThat(page.getContent())
                .anySatisfy(item -> {
                    assertThat(item.request().getId()).isEqualTo(requestId);
                    assertThat(item.assignment()).isNotNull();
                    assertThat(item.assignment().getTeam().getId())
                            .isEqualTo(TEAM_ID);
                    assertThat(item.assignment().getStatus())
                            .isEqualTo("ACCEPTED");
                });
    }

    private CoordinatorCloseRequest command(
            Long version,
            String closeNote
    ) {
        CoordinatorCloseRequest command = new CoordinatorCloseRequest();
        command.setVersion(version);
        command.setCloseNote(closeNote);
        return command;
    }

    private void insertRequest(
            UUID requestId,
            String status,
            long version
    ) {
        jdbcTemplate.update("""
                INSERT INTO rescue_requests (
                    id,
                    display_code,
                    user_id,
                    disaster_type_id,
                    urgency,
                    status,
                    address,
                    description,
                    created_at,
                    updated_at,
                    version
                )
                VALUES (
                    UNHEX(REPLACE(?, '-', '')),
                    ?,
                    UNHEX(REPLACE(?, '-', '')),
                    (SELECT id FROM disaster_types
                     WHERE disaster_code = 'DIS-001'),
                    'HIGH',
                    ?,
                    'T3-03 integration address',
                    'T3-03 monitor and close request',
                    NOW(6),
                    NOW(6),
                    ?
                )
                """,
                requestId.toString(),
                displayCode("REQ-T303-", requestId),
                CITIZEN_ID.toString(),
                status,
                version
        );
    }

    private void insertAssignment(
            UUID requestId,
            String status,
            LocalDateTime completionTime,
            String notes
    ) {
        UUID assignmentId = UUID.randomUUID();
        jdbcTemplate.update("""
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
                )
                VALUES (
                    UNHEX(REPLACE(?, '-', '')),
                    ?,
                    UNHEX(REPLACE(?, '-', '')),
                    UNHEX(REPLACE(?, '-', '')),
                    ?,
                    DATE_SUB(NOW(6), INTERVAL 10 MINUTE),
                    ?,
                    ?,
                    0,
                    NOW(6),
                    NOW(6)
                )
                """,
                assignmentId.toString(),
                displayCode("ASN-T303-", assignmentId),
                requestId.toString(),
                TEAM_ID.toString(),
                status,
                completionTime,
                notes
        );
    }

    private String requestStatus(UUID requestId) {
        return jdbcTemplate.queryForObject("""
                SELECT status
                FROM rescue_requests
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """, String.class, requestId.toString());
    }

    private long transitionCount(
            UUID requestId,
            String oldStatus,
            String newStatus
    ) {
        return jdbcTemplate.queryForObject("""
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
    }

    private long closedNotificationCount(UUID requestId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM notifications
                WHERE reference_id = UNHEX(REPLACE(?, '-', ''))
                  AND event_type = 'REQUEST_CLOSED'
                """,
                Long.class,
                requestId.toString()
        );
    }

    private String displayCode(String prefix, UUID id) {
        return prefix + id.toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }
}