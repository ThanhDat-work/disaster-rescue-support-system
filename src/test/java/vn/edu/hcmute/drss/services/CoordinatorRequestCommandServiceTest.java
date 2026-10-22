package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionAction;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionRequest;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class CoordinatorRequestCommandServiceTest {

    @Mock
    private RescueRequestRepository requests;
    @Mock
    private StatusHistoryRepository histories;
    @Mock
    private UserRepository users;
    @Mock
    private BusinessNotificationService notifications;

    private CoordinatorRequestCommandService service;
    private UUID requestId;
    private UUID coordinatorId;
    private User coordinator;
    private RescueRequest request;

    @BeforeEach
    void setUp() {
        service = new CoordinatorRequestCommandService(
                requests,
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
        request.setStatus("PENDING");
        request.setVersion(3L);
    }

    @Test
    void verifyMovesPendingToVerifiedAndCreatesOneHistory() {
        allowDecision();

        String result = service.decide(
                requestId,
                coordinatorId,
                command(CoordinatorDecisionAction.VERIFY, 3L, null)
        );

        assertThat(result).isEqualTo("VERIFIED");
        assertThat(request.getStatus()).isEqualTo("VERIFIED");

        ArgumentCaptor<StatusHistory> history =
                ArgumentCaptor.forClass(StatusHistory.class);
        verify(histories).save(history.capture());
        verify(requests).flush();
        verify(notifications).notifyRequestStatusChanged(
                request,
                "PENDING",
                "VERIFIED"
        );

        assertThat(history.getValue().getRequest()).isSameAs(request);
        assertThat(history.getValue().getOldStatus()).isEqualTo("PENDING");
        assertThat(history.getValue().getNewStatus()).isEqualTo("VERIFIED");
        assertThat(history.getValue().getChangedByUser())
                .isSameAs(coordinator);
        assertThat(history.getValue().getNotes())
                .isEqualTo("Điều phối viên đã xác minh yêu cầu.");
        assertThat(history.getValue().getActorRole())
                .isEqualTo("ROLE_COORDINATOR");
    }

    @Test
    void rejectMovesPendingToRejectedAndTrimsReason() {
        allowDecision();

        String result = service.decide(
                requestId,
                coordinatorId,
                command(
                        CoordinatorDecisionAction.REJECT,
                        3L,
                        "  Thiếu thông tin vị trí  "
                )
        );

        assertThat(result).isEqualTo("REJECTED");
        assertThat(request.getStatus()).isEqualTo("REJECTED");

        ArgumentCaptor<StatusHistory> history =
                ArgumentCaptor.forClass(StatusHistory.class);
        verify(histories).save(history.capture());
        verify(requests).flush();
        verify(notifications).notifyRequestStatusChanged(
                request,
                "PENDING",
                "REJECTED"
        );

        assertThat(history.getValue().getOldStatus()).isEqualTo("PENDING");
        assertThat(history.getValue().getNewStatus()).isEqualTo("REJECTED");
        assertThat(history.getValue().getNotes())
                .isEqualTo("Thiếu thông tin vị trí");
        assertThat(history.getValue().getActorRole())
                .isEqualTo("ROLE_COORDINATOR");
    }

    @Test
    void rejectRequiresReasonAndEnforcesMaximumLength() {
        allowDecision();

        assertThatThrownBy(() -> service.decide(
                requestId,
                coordinatorId,
                command(CoordinatorDecisionAction.REJECT, 3L, "   ")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vui lòng nhập lý do từ chối.");

        assertThatThrownBy(() -> service.decide(
                requestId,
                coordinatorId,
                command(
                        CoordinatorDecisionAction.REJECT,
                        3L,
                        "x".repeat(2001)
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lý do từ chối tối đa 2.000 ký tự.");

        verify(histories, never()).save(any());
        verify(requests, never()).flush();
        verify(notifications, never()).notifyRequestStatusChanged(
                any(),
                any(),
                any()
        );
    }

    @Test
    void staleFormVersionIsRejectedBeforeMutation() {
        allowDecision();

        assertThatThrownBy(() -> service.decide(
                requestId,
                coordinatorId,
                command(CoordinatorDecisionAction.VERIFY, 2L, null)
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("vừa được cập nhật");

        assertThat(request.getStatus()).isEqualTo("PENDING");
        verify(histories, never()).save(any());
        verify(requests, never()).flush();
    }

    @ParameterizedTest
    @MethodSource("nonPendingStatuses")
    void everyNonPendingStateRejectsAnotherDecision(String status) {
        request.setStatus(status);
        allowDecision();

        assertThatThrownBy(() -> service.decide(
                requestId,
                coordinatorId,
                command(CoordinatorDecisionAction.VERIFY, 3L, null)
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("PENDING");

        assertThat(request.getStatus()).isEqualTo(status);
        verify(histories, never()).save(any());
        verify(requests, never()).flush();
    }

    @Test
    void missingRequestReturnsNotFoundWithoutHistory() {
        when(users.findById(coordinatorId))
                .thenReturn(Optional.of(coordinator));
        when(requests.findByIdForCoordinatorAction(requestId, coordinatorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(
                requestId,
                coordinatorId,
                command(CoordinatorDecisionAction.VERIFY, 3L, null)
        )).isInstanceOf(CoordinatorRequestNotFoundException.class);

        verify(histories, never()).save(any());
        verify(requests, never()).flush();
    }

    @Test
    void ownerOrForgedRequestIsNotFoundWithoutHistory() {
        when(users.findById(coordinatorId)).thenReturn(Optional.of(coordinator));
        when(requests.findByIdForCoordinatorAction(requestId, coordinatorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(
                requestId, coordinatorId,
                command(CoordinatorDecisionAction.VERIFY, 3L, null)
        )).isInstanceOf(CoordinatorRequestNotFoundException.class);

        verify(histories, never()).save(any());
        verify(notifications, never()).notifyRequestStatusChanged(
                any(), any(), any());
    }

    @Test
    void missingCoordinatorIsRejectedBeforeRequestMutation() {
        when(users.findById(coordinatorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.decide(
                requestId,
                coordinatorId,
                command(CoordinatorDecisionAction.VERIFY, 3L, null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("điều phối viên");

        verify(requests, never()).findByIdForCoordinatorAction(any(), any());
        verify(histories, never()).save(any());
    }

    @Test
    void optimisticFailurePropagatesSoTransactionCanRollback() {
        allowDecision();
        doThrow(new OptimisticLockingFailureException("stale"))
                .when(requests)
                .flush();

        assertThatThrownBy(() -> service.decide(
                requestId,
                coordinatorId,
                command(CoordinatorDecisionAction.VERIFY, 3L, null)
        )).isInstanceOf(OptimisticLockingFailureException.class);

        verify(histories).save(any(StatusHistory.class));
        verify(requests).flush();
    }

    @Test
    void missingActionAndVersionAreRejected() {
        CoordinatorDecisionRequest missingAction =
                command(null, 3L, null);

        assertThatThrownBy(() -> service.decide(
                requestId,
                coordinatorId,
                missingAction
        )).isInstanceOf(IllegalArgumentException.class);

        allowDecision();

        assertThatThrownBy(() -> service.decide(
                requestId,
                coordinatorId,
                command(CoordinatorDecisionAction.VERIFY, null, null)
        )).isInstanceOf(CoordinatorRequestConflictException.class);
    }

    private void allowDecision() {
        when(users.findById(coordinatorId))
                .thenReturn(Optional.of(coordinator));
        when(requests.findByIdForCoordinatorAction(requestId, coordinatorId))
                .thenReturn(Optional.of(request));
    }

    private CoordinatorDecisionRequest command(
            CoordinatorDecisionAction action,
            Long version,
            String reason
    ) {
        CoordinatorDecisionRequest command =
                new CoordinatorDecisionRequest();
        command.setAction(action);
        command.setVersion(version);
        command.setReason(reason);
        return command;
    }

    private static Stream<String> nonPendingStatuses() {
        return Stream.of(
                "VERIFIED",
                "ASSIGNED",
                "IN_PROGRESS",
                "RESCUED",
                "CLOSED",
                "REJECTED",
                "CANCELLED"
        );
    }
}