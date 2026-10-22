package vn.edu.hcmute.drss;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.hcmute.drss.dtos.CoordinatorAssignmentRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorCloseRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionAction;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionRequest;
import vn.edu.hcmute.drss.services.CitizenRequestService;
import vn.edu.hcmute.drss.services.CoordinatorAssignmentService;
import vn.edu.hcmute.drss.services.CoordinatorCloseService;
import vn.edu.hcmute.drss.services.CoordinatorRequestCommandService;
import vn.edu.hcmute.drss.services.RescuerService;

@SpringBootTest
@ActiveProfiles("it")
class ReleaseConcurrencyIntegrationIT {

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
    private CitizenRequestService citizenService;
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

    private final Set<UUID> requestIds = new HashSet<>();

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
    }

    @Test
    void concurrentDecisionCreatesOneTransitionOnly() throws Exception {
        UUID requestId = insertRequest("PENDING");

        RaceResult result = race(
                () -> commandService.decide(
                        requestId,
                        COORDINATOR_ID,
                        decisionCommand()
                ),
                () -> commandService.decide(
                        requestId,
                        COORDINATOR_ID,
                        decisionCommand()
                )
        );

        assertSingleWinner(result);
        assertThat(status(requestId)).isEqualTo("VERIFIED");
        assertAuditTrail(requestId, 1L);
        assertThat(requestNotificationCount(requestId)).isEqualTo(1L);
    }

    @Test
    void cancelAndVerifyRaceSerializesLegalTransitions() throws Exception {
        UUID requestId = insertRequest("PENDING");

        RaceResult result = race(
                () -> citizenService.cancel(
                        requestId,
                        CITIZEN_ID,
                        "Người dân xác nhận không còn cần hỗ trợ."
                ),
                () -> commandService.decide(
                        requestId,
                        COORDINATOR_ID,
                        decisionCommand()
                )
        );

        /*
        * Cancel chạy trước:
        * PENDING -> CANCELLED, verify bị từ chối.
        *
        * Verify chạy trước:
        * PENDING -> VERIFIED -> CANCELLED.
        * Cả hai transition đều hợp lệ theo business rule.
        */
        assertThat(result.successes()).isBetween(1, 2);
        assertThat(result.successes() + result.failures().size())
                .isEqualTo(2);
        assertThat(status(requestId)).isEqualTo("CANCELLED");

        assertAuditTrail(requestId, result.successes());
        assertThat(requestNotificationCount(requestId))
                .isEqualTo(result.successes());

        if (result.successes() == 1) {
            assertThat(result.failures()).hasSize(1);
            assertThat(result.failures().get(0))
                    .isInstanceOf(RuntimeException.class);
        } else {
            assertThat(result.failures()).isEmpty();
        }
    }

    @Test
    void concurrentAssignmentCreatesOneActiveAssignment() throws Exception {
        UUID requestId = insertRequest("VERIFIED");

        RaceResult result = race(
                () -> assignmentService.assign(
                        requestId,
                        COORDINATOR_ID,
                        assignmentCommand()
                ),
                () -> assignmentService.assign(
                        requestId,
                        COORDINATOR_ID,
                        assignmentCommand()
                )
        );

        assertSingleWinner(result);
        assertThat(status(requestId)).isEqualTo("ASSIGNED");
        assertThat(assignmentCount(requestId)).isEqualTo(1L);
        assertAuditTrail(requestId, 1L);
        assertThat(requestNotificationCount(requestId)).isEqualTo(1L);
    }

    @Test
    void concurrentRescuerStartCreatesOneTransitionOnly()
            throws Exception {
        UUID requestId = insertRequest("ASSIGNED");
        UUID assignmentId = insertAssignment(
                requestId,
                "ASSIGNED",
                null,
                null
        );

        RaceResult result = race(
                () -> rescuerService.startRescue(
                        assignmentId,
                        RESCUER_ID,
                        "Đội bắt đầu tiếp cận hiện trường."
                ),
                () -> rescuerService.startRescue(
                        assignmentId,
                        RESCUER_ID,
                        "Đội bắt đầu tiếp cận hiện trường."
                )
        );

        assertSingleWinner(result);
        assertThat(status(requestId)).isEqualTo("IN_PROGRESS");
        assertThat(assignmentStatus(assignmentId)).isEqualTo("ACCEPTED");
        assertAuditTrail(requestId, 1L);
        assertThat(requestNotificationCount(requestId)).isEqualTo(1L);
    }

    @Test
    void concurrentCloseCreatesOneClosedAuditEntry() throws Exception {
        UUID requestId = insertRequest("RESCUED");
        insertAssignment(
                requestId,
                "COMPLETED",
                LocalDateTime.now(),
                "Đã hoàn tất cứu nạn và bàn giao an toàn."
        );

        RaceResult result = race(
                () -> closeService.close(
                        requestId,
                        COORDINATOR_ID,
                        closeCommand()
                ),
                () -> closeService.close(
                        requestId,
                        COORDINATOR_ID,
                        closeCommand()
                )
        );

        assertSingleWinner(result);
        assertThat(status(requestId)).isEqualTo("CLOSED");
        assertAuditTrail(requestId, 1L);
        assertThat(requestNotificationCount(requestId)).isEqualTo(1L);
    }

    private RaceResult race(Runnable first, Runnable second)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = Collections.synchronizedList(
                new ArrayList<>()
        );

        Runnable workerOne = worker(
                first,
                ready,
                start,
                successes,
                failures
        );
        Runnable workerTwo = worker(
                second,
                ready,
                start,
                successes,
                failures
        );

        try {
            Future<?> firstFuture = executor.submit(workerOne);
            Future<?> secondFuture = executor.submit(workerTwo);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstFuture.get(30, TimeUnit.SECONDS);
            secondFuture.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        return new RaceResult(successes.get(), List.copyOf(failures));
    }

    private Runnable worker(
            Runnable action,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successes,
            List<Throwable> failures
    ) {
        return () -> {
            ready.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Race start timeout");
                }
                action.run();
                successes.incrementAndGet();
            } catch (Throwable failure) {
                failures.add(failure);
            }
        };
    }

    private void assertSingleWinner(RaceResult result) {
        assertThat(result.successes()).isEqualTo(1);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0))
                .isInstanceOf(RuntimeException.class);
    }

    private void assertAuditTrail(UUID requestId, long expected) {
        assertThat(historyCount(requestId)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM status_histories
                WHERE request_id = UNHEX(REPLACE(?, '-', ''))
                  AND changed_by_user_id IS NOT NULL
                  AND created_at IS NOT NULL
                  AND notes IS NOT NULL
                  AND TRIM(notes) <> ''
                """, Long.class, requestId.toString()))
                .isEqualTo(expected);
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
                    'HIGH', ?, 'Release concurrency address',
                    'Release concurrency integration request',
                    NOW(6), NOW(6), 0
                )
                """,
                requestId.toString(),
                code("REQ-RACE-", requestId),
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
                code("ASN-RACE-", assignmentId),
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
        command.setCoordinatorNote("Concurrent assignment test");
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

    private long requestNotificationCount(UUID requestId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications
                WHERE reference_type = 'RESCUE_REQUEST'
                  AND reference_id = UNHEX(REPLACE(?, '-', ''))
                """, Long.class, requestId.toString());
    }

    private String code(String prefix, UUID id) {
        return prefix + id.toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }

    private record RaceResult(
            int successes,
            List<Throwable> failures
    ) {
    }
}