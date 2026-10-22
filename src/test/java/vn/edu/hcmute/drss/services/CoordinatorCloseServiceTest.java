package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import vn.edu.hcmute.drss.dtos.CoordinatorCloseRequest;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class CoordinatorCloseServiceTest {

    @Mock
    private RescueRequestRepository requests;
    @Mock
    private RescueAssignmentRepository assignments;
    @Mock
    private StatusHistoryRepository histories;
    @Mock
    private UserRepository users;
    @Mock
    private BusinessNotificationService notifications;

    private CoordinatorCloseService service;
    private UUID requestId;
    private UUID coordinatorId;
    private RescueRequest request;
    private RescueAssignment assignment;
    private User coordinator;

    @BeforeEach
    void setUp() {
        service = new CoordinatorCloseService(
                requests,
                assignments,
                histories,
                users,
                notifications
        );
        requestId = UUID.randomUUID();
        coordinatorId = UUID.randomUUID();

        coordinator = new User();
        coordinator.setId(coordinatorId);

        request = new RescueRequest();
        request.setId(requestId);
        request.setStatus("RESCUED");
        request.setVersion(4L);

        assignment = new RescueAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setRequest(request);
        assignment.setStatus("COMPLETED");
        assignment.setCompletionTime(LocalDateTime.now());
        assignment.setNotes("Đã đưa người dân đến nơi an toàn.");
    }

    @Test
    void closeMovesRescuedToClosedWithHistoryAndNotification() {
        allowClose();

        RescueRequest result = service.close(
                requestId,
                coordinatorId,
                command(4L, "  Hoàn tất cứu nạn an toàn.  ")
        );

        assertThat(result).isSameAs(request);
        assertThat(request.getStatus()).isEqualTo("CLOSED");

        ArgumentCaptor<StatusHistory> history =
                ArgumentCaptor.forClass(StatusHistory.class);
        verify(histories).save(history.capture());
        verify(requests).flush();
        verify(notifications).notifyRequestStatusChanged(
                request,
                "RESCUED",
                "CLOSED"
        );
        assertThat(history.getValue().getRequest()).isSameAs(request);
        assertThat(history.getValue().getChangedByUser())
                .isSameAs(coordinator);
        assertThat(history.getValue().getOldStatus()).isEqualTo("RESCUED");
        assertThat(history.getValue().getNewStatus()).isEqualTo("CLOSED");
        assertThat(history.getValue().getNotes())
                .isEqualTo("Hoàn tất cứu nạn an toàn.");
    }

    @Test
    void staleVersionAndEveryNonRescuedStateAreRejected() {
        when(users.findById(coordinatorId))
                .thenReturn(Optional.of(coordinator));
        when(requests.findByIdForCoordinatorAssignment(requestId))
                .thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.close(
                requestId,
                coordinatorId,
                command(3L, "Hoàn tất cứu nạn an toàn.")
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("vừa được cập nhật");

        request.setStatus("CLOSED");
        assertThatThrownBy(() -> service.close(
                requestId,
                coordinatorId,
                command(4L, "Hoàn tất cứu nạn an toàn.")
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("RESCUED");

        verify(histories, never()).save(any());
        verify(notifications, never())
                .notifyRequestStatusChanged(any(), any(), any());
    }

    @Test
    void incompleteAssignmentReportBlocksClose() {
        allowClose();
        assignment.setNotes("   ");

        assertThatThrownBy(() -> service.close(
                requestId,
                coordinatorId,
                command(4L, "Hoàn tất cứu nạn an toàn.")
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("báo cáo hoàn thành");

        assertThat(request.getStatus()).isEqualTo("RESCUED");
        verify(histories, never()).save(any());
    }

    @Test
    void assignmentMustBeCompletedAndHaveCompletionTime() {
        allowClose();
        assignment.setStatus("ACCEPTED");

        assertThatThrownBy(() -> service.close(
                requestId,
                coordinatorId,
                command(4L, "Hoàn tất cứu nạn an toàn.")
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("COMPLETED");

        assignment.setStatus("COMPLETED");
        assignment.setCompletionTime(null);
        assertThatThrownBy(() -> service.close(
                requestId,
                coordinatorId,
                command(4L, "Hoàn tất cứu nạn an toàn.")
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("thời gian hoàn thành");
    }

    @Test
    void closeNoteIsValidatedAgainInsideService() {
        allowClose();

        assertThatThrownBy(() -> service.close(
                requestId,
                coordinatorId,
                command(4L, "ngắn")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 đến 500");

        assertThatThrownBy(() -> service.close(
                requestId,
                coordinatorId,
                command(4L, "x".repeat(501))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 đến 500");
    }

    @Test
    void flushFailurePropagatesForTransactionRollback() {
        allowClose();
        org.mockito.Mockito.doThrow(
                new OptimisticLockingFailureException("stale")
        ).when(requests).flush();

        assertThatThrownBy(() -> service.close(
                requestId,
                coordinatorId,
                command(4L, "Hoàn tất cứu nạn an toàn.")
        )).isInstanceOf(OptimisticLockingFailureException.class);

        verify(notifications, never())
                .notifyRequestStatusChanged(any(), any(), any());
    }

    private void allowClose() {
        when(users.findById(coordinatorId))
                .thenReturn(Optional.of(coordinator));
        when(requests.findByIdForCoordinatorAssignment(requestId))
                .thenReturn(Optional.of(request));
        when(assignments
                .findFirstByRequest_IdOrderByAssignedAtDescIdDesc(requestId))
                .thenReturn(Optional.of(assignment));
    }

    private CoordinatorCloseRequest command(Long version, String closeNote) {
        CoordinatorCloseRequest command = new CoordinatorCloseRequest();
        command.setVersion(version);
        command.setCloseNote(closeNote);
        return command;
    }
}