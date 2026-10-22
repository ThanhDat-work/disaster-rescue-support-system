package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import vn.edu.hcmute.drss.dtos.CoordinatorAssignmentRequest;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class CoordinatorAssignmentServiceTest {

    @Mock
    private RescueRequestRepository requests;
    @Mock
    private RescueTeamRepository teams;
    @Mock
    private RescueTeamMemberRepository teamMembers;
    @Mock
    private RescueAssignmentRepository assignments;
    @Mock
    private StatusHistoryRepository histories;
    @Mock
    private UserRepository users;
    @Mock
    private BusinessNotificationService notifications;

    private CoordinatorAssignmentService service;
    private UUID requestId;
    private UUID teamId;
    private UUID coordinatorId;
    private RescueRequest request;
    private RescueTeam team;
    private User coordinator;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new CoordinatorAssignmentService(
                requests,
                teams,
                teamMembers,
                assignments,
                histories,
                users,
                notifications
        );

        requestId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        coordinatorId = UUID.randomUUID();

        request = new RescueRequest();
        request.setId(requestId);
        request.setStatus("VERIFIED");
        request.setVersion(4L);
        owner = new User();
        owner.setId(UUID.randomUUID());
        request.setUser(owner);

        team = new RescueTeam();
        team.setId(teamId);
        team.setDisplayCode("TEAM-001");
        team.setTeamName("Đội Phản Ứng Nhanh");
        team.setStatus("AVAILABLE");

        coordinator = new User();
        coordinator.setId(coordinatorId);
    }

    @Test
    void availableTeamQueryUsesRepositoryOrder() {
        when(teams.findAvailableWithActiveMembersForAssignmentExcludingOwner(owner.getId()))
                .thenReturn(List.of(team));

        assertThat(service.findAvailableTeams(owner.getId())).containsExactly(team);
    }

    @Test
    void assignCreatesAssignmentHistoryAndMovesRequestAtomically() {
        allowSuccessfulAssignment();

        RescueAssignment result = service.assign(
                requestId,
                coordinatorId,
                command(teamId, 4L, "  Ưu tiên đường chính  ")
        );

        assertThat(request.getStatus()).isEqualTo("ASSIGNED");
        assertThat(team.getStatus()).isEqualTo("AVAILABLE");
        assertThat(result.getId()).isNotNull();
        assertThat(result.getDisplayCode())
                .matches("ASN-[0-9A-F]{16}");
        assertThat(result.getRequest()).isSameAs(request);
        assertThat(result.getTeam()).isSameAs(team);
        assertThat(result.getStatus()).isEqualTo("ASSIGNED");
        assertThat(result.getAssignedAt()).isNotNull();
        assertThat(result.getCompletionTime()).isNull();
        assertThat(result.getNotes()).isNull();
        assertThat(result.getVersion()).isZero();

        verify(assignments).save(result);

        ArgumentCaptor<StatusHistory> history =
                ArgumentCaptor.forClass(StatusHistory.class);
        verify(histories).save(history.capture());
        verify(requests).flush();
        verify(notifications).notifyAssignmentCreated(result);
        verify(notifications).notifyRequestStatusChanged(
                request,
                "VERIFIED",
                "ASSIGNED"
        );

        assertThat(history.getValue().getRequest()).isSameAs(request);
        assertThat(history.getValue().getOldStatus()).isEqualTo("VERIFIED");
        assertThat(history.getValue().getNewStatus()).isEqualTo("ASSIGNED");
        assertThat(history.getValue().getActorRole())
                .isEqualTo("ROLE_COORDINATOR");
        assertThat(history.getValue().getChangedByUser())
                .isSameAs(coordinator);
        assertThat(history.getValue().getNotes())
                .contains("TEAM-001")
                .contains("Ưu tiên đường chính");
    }

    @Test
    void staleVersionIsRejectedBeforeAssignmentLookup() {
        allowCoordinatorAndRequest();

        assertThatThrownBy(() -> service.assign(
                requestId,
                coordinatorId,
                command(teamId, 3L, null)
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("vừa được cập nhật");

        assertThat(request.getStatus()).isEqualTo("VERIFIED");
        verify(assignments, never())
                .existsByRequest_IdAndStatusIn(any(), any());
        verify(assignments, never()).save(any());
        verify(histories, never()).save(any());
    }

    @Test
    void nonVerifiedRequestCannotBeAssigned() {
        request.setStatus("CANCELLED");
        allowCoordinatorAndRequest();

        assertThatThrownBy(() -> service.assign(
                requestId,
                coordinatorId,
                command(teamId, 4L, null)
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("VERIFIED");

        assertThat(request.getStatus()).isEqualTo("CANCELLED");
        verify(assignments, never()).save(any());
        verify(histories, never()).save(any());
    }

    @Test
    void activeAssignmentBlocksDoubleAssignment() {
        allowCoordinatorAndRequest();
        when(assignments.existsByRequest_IdAndStatusIn(
                requestId,
                CoordinatorAssignmentServiceActiveStatuses.values()
        )).thenReturn(true);

        assertThatThrownBy(() -> service.assign(
                requestId,
                coordinatorId,
                command(teamId, 4L, null)
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("đã có phân công");

        verify(teams, never()).findByIdForCoordinatorAssignment(any());
        verify(assignments, never()).save(any());
        verify(histories, never()).save(any());
    }

    @Test
    void missingOrUnavailableTeamIsRejectedWithoutMutation() {
        allowCoordinatorRequestAndNoAssignment();
        when(teams.findByIdForCoordinatorAssignment(teamId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(
                requestId,
                coordinatorId,
                command(teamId, 4L, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy đội");

        team.setStatus("OFFLINE");
        when(teams.findByIdForCoordinatorAssignment(teamId))
                .thenReturn(Optional.of(team));

        assertThatThrownBy(() -> service.assign(
                requestId,
                coordinatorId,
                command(teamId, 4L, null)
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("không khả dụng");

        assertThat(request.getStatus()).isEqualTo("VERIFIED");
        verify(assignments, never()).save(any());
        verify(histories, never()).save(any());
    }

    @Test
    void teamWithoutActiveMemberIsRejected() {
        allowCoordinatorRequestAndNoAssignment();
        when(teams.findByIdForCoordinatorAssignment(teamId))
                .thenReturn(Optional.of(team));
        when(teamMembers.existsByTeam_IdAndLeftAtIsNull(teamId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.assign(
                requestId,
                coordinatorId,
                command(teamId, 4L, null)
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("không có thành viên");

        assertThat(request.getStatus()).isEqualTo("VERIFIED");
        verify(assignments, never()).save(any());
        verify(histories, never()).save(any());
    }

    @Test
    void teamContainingRequestOwnerCannotBeAssigned() {
        allowCoordinatorRequestAndNoAssignment();
        when(teams.findByIdForCoordinatorAssignment(teamId))
                .thenReturn(Optional.of(team));
        when(teamMembers.existsByTeam_IdAndLeftAtIsNull(teamId))
                .thenReturn(true);
        when(teamMembers.existsByTeam_IdAndUser_IdAndLeftAtIsNull(
                teamId, owner.getId()
        )).thenReturn(true);

        assertThatThrownBy(() -> service.assign(
                requestId, coordinatorId, command(teamId, 4L, null)
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("người gửi yêu cầu");

        verify(assignments, never()).save(any());
        verify(histories, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void missingRequestOrCoordinatorIsRejected() {
        when(users.findById(coordinatorId))
                .thenReturn(Optional.of(coordinator));
        when(requests.findByIdForCoordinatorAction(requestId, coordinatorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(
                requestId,
                coordinatorId,
                command(teamId, 4L, null)
        )).isInstanceOf(CoordinatorRequestNotFoundException.class);

        UUID missingCoordinatorId = UUID.randomUUID();
        when(users.findById(missingCoordinatorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(
                requestId,
                missingCoordinatorId,
                command(teamId, 4L, null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("điều phối viên");

        verify(assignments, never()).save(any());
        verify(histories, never()).save(any());
    }

    @Test
    void malformedCommandIsRejectedBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.assign(
                requestId,
                coordinatorId,
                command(null, 4L, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chọn đội");

        assertThatThrownBy(() -> service.assign(
                requestId,
                coordinatorId,
                command(teamId, 4L, "x".repeat(2001))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2.000");

        verify(users, never()).findById(any());
        verify(requests, never()).findByIdForCoordinatorAction(any(), any());
    }

    @Test
    void flushFailurePropagatesForTransactionRollback() {
        allowSuccessfulAssignment();
        doThrow(new DataIntegrityViolationException("constraint"))
                .when(requests)
                .flush();

        assertThatThrownBy(() -> service.assign(
                requestId,
                coordinatorId,
                command(teamId, 4L, null)
        )).isInstanceOf(DataIntegrityViolationException.class);

        verify(assignments).save(any(RescueAssignment.class));
        verify(histories).save(any(StatusHistory.class));
        verify(requests).flush();
    }

    private void allowCoordinatorAndRequest() {
        when(users.findById(coordinatorId))
                .thenReturn(Optional.of(coordinator));
        when(requests.findByIdForCoordinatorAction(requestId, coordinatorId))
                .thenReturn(Optional.of(request));
    }

    private void allowCoordinatorRequestAndNoAssignment() {
        allowCoordinatorAndRequest();
        when(assignments.existsByRequest_IdAndStatusIn(
                requestId,
                CoordinatorAssignmentServiceActiveStatuses.values()
        )).thenReturn(false);
    }

    private void allowSuccessfulAssignment() {
        allowCoordinatorRequestAndNoAssignment();
        when(teams.findByIdForCoordinatorAssignment(teamId))
                .thenReturn(Optional.of(team));
        when(teamMembers.existsByTeam_IdAndLeftAtIsNull(teamId))
                .thenReturn(true);
        when(teamMembers.existsByTeam_IdAndUser_IdAndLeftAtIsNull(
                teamId, owner.getId()
        )).thenReturn(false);
        when(assignments.save(any(RescueAssignment.class)))
                .thenAnswer(invocation -> {
                        RescueAssignment saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                });
        }

    private CoordinatorAssignmentRequest command(
            UUID selectedTeamId,
            Long version,
            String note
    ) {
        CoordinatorAssignmentRequest command =
                new CoordinatorAssignmentRequest();
        command.setTeamId(selectedTeamId);
        command.setVersion(version);
        command.setCoordinatorNote(note);
        return command;
    }

    /**
     * Test helper giữ đúng tập trạng thái active mà service contract dùng.
     */
    private static final class CoordinatorAssignmentServiceActiveStatuses {
        private CoordinatorAssignmentServiceActiveStatuses() {
        }

        private static java.util.Set<String> values() {
            return java.util.Set.of("ASSIGNED", "ACCEPTED");
        }
    }
}