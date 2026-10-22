package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.entities.RescueTeamMember;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RescuerServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    private static final UUID TEAM_ID =
            UUID.fromString(
                    "55555555-5555-5555-5555-555555555555"
            );

    private static final UUID ASSIGNMENT_ID =
            UUID.fromString(
                    "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
            );

    @Mock
    private RescueAssignmentRepository assignmentRepository;

    @Mock
    private RescueTeamMemberRepository teamMemberRepository;

    @Mock
    private StatusHistoryRepository statusHistoryRepository;

    @Mock
    private BusinessNotificationService notifications;

    @InjectMocks
    private RescuerService rescuerService;

    private User rescuer;
    private RescueTeam team;
    private RescueRequest request;
    private RescueAssignment assignment;
    private RescueTeamMember membership;

    @BeforeEach
    void setUp() {
        rescuer = new User();
        rescuer.setId(USER_ID);
        rescuer.setEmail("cuuho@demo.com");
        rescuer.setFullName("Nhân viên cứu hộ Demo");

        team = new RescueTeam();
        team.setId(TEAM_ID);
        team.setDisplayCode("TEAM-001");
        team.setTeamName("Đội cứu hộ Demo");

        request = new RescueRequest();
        request.setDisplayCode("REQ-0002");
        request.setStatus("ASSIGNED");
        request.setUrgency("HIGH");

        assignment = new RescueAssignment();
        assignment.setId(ASSIGNMENT_ID);
        assignment.setDisplayCode("ASN-0001");
        assignment.setTeam(team);
        assignment.setRequest(request);
        assignment.setStatus("ASSIGNED");

        membership = new RescueTeamMember();
        membership.setUser(rescuer);
        membership.setTeam(team);
membership.setLeftAt(null);
    }

    @Test
    void getAssignmentsReturnsOnlyRepositoryResultForUser() {
        when(assignmentRepository.findAssignmentsByUserId(USER_ID))
                .thenReturn(List.of(assignment));

        List<RescueAssignment> result =
                rescuerService.getAssignmentsForUser(USER_ID);

        assertThat(result).containsExactly(assignment);

        verify(assignmentRepository)
                .findAssignmentsByUserId(USER_ID);
    }

    @Test
    void inaccessibleAssignmentIsRejected() {
        when(assignmentRepository.findByIdAndUserId(
                ASSIGNMENT_ID,
                USER_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                rescuerService.getAssignmentDetail(
                        ASSIGNMENT_ID,
                        USER_ID
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không có quyền");
    }

    @Test
    void startRescueUpdatesBothStatesAndCreatesHistory() {
        allowAssignmentAccess();
        allowActiveMembership();

        rescuerService.startRescue(
                ASSIGNMENT_ID,
                USER_ID,
                "  Đã tiếp cận hiện trường.  "
        );

        assertThat(request.getStatus())
                .isEqualTo("IN_PROGRESS");

        assertThat(assignment.getStatus())
                .isEqualTo("ACCEPTED");

        ArgumentCaptor<StatusHistory> historyCaptor =
                ArgumentCaptor.forClass(StatusHistory.class);

        verify(statusHistoryRepository)
                .save(historyCaptor.capture());

        StatusHistory history = historyCaptor.getValue();

        assertThat(history.getRequest())
                .isSameAs(request);

        assertThat(history.getChangedByUser())
                .isSameAs(rescuer);

        assertThat(history.getOldStatus())
                .isEqualTo("ASSIGNED");

        assertThat(history.getNewStatus())
                .isEqualTo("IN_PROGRESS");

        assertThat(history.getNotes())
                .isEqualTo("Đã tiếp cận hiện trường.");

        verify(assignmentRepository).save(assignment);
        verify(assignmentRepository).flush();
        verify(notifications).notifyRequestStatusChanged(
                request,
                "ASSIGNED",
                "IN_PROGRESS"
        );
    }

    @Test
    void startRescueRejectsInvalidCurrentState() {
        request.setStatus("IN_PROGRESS");

        allowAssignmentAccess();
        allowActiveMembership();

        assertThatThrownBy(() ->
                rescuerService.startRescue(
                        ASSIGNMENT_ID,
                        USER_ID,
                        null
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ASSIGNED");

        verify(assignmentRepository, never())
                .save(assignment);

        verify(statusHistoryRepository, never())
                .save(
                        org.mockito.ArgumentMatchers
                                .any(StatusHistory.class)
);
    }

    @Test
    void completeRescueUpdatesStatesTimeNotesAndHistory() {
        request.setStatus("IN_PROGRESS");
        assignment.setStatus("ACCEPTED");

        allowAssignmentAccess();
        allowActiveMembership();

        rescuerService.completeRescue(
                ASSIGNMENT_ID,
                USER_ID,
                "  Đã đưa người dân đến nơi an toàn.  "
        );

        assertThat(request.getStatus())
                .isEqualTo("RESCUED");

        assertThat(assignment.getStatus())
                .isEqualTo("COMPLETED");

        assertThat(assignment.getCompletionTime())
                .isNotNull();

        assertThat(assignment.getNotes())
                .isEqualTo(
                        "Đã đưa người dân đến nơi an toàn."
                );

        ArgumentCaptor<StatusHistory> historyCaptor =
                ArgumentCaptor.forClass(StatusHistory.class);

        verify(statusHistoryRepository)
                .save(historyCaptor.capture());

        StatusHistory history = historyCaptor.getValue();

        assertThat(history.getRequest())
                .isSameAs(request);

        assertThat(history.getChangedByUser())
                .isSameAs(rescuer);

        assertThat(history.getOldStatus())
                .isEqualTo("IN_PROGRESS");

        assertThat(history.getNewStatus())
                .isEqualTo("RESCUED");

        verify(assignmentRepository).save(assignment);
        verify(assignmentRepository).flush();
        verify(notifications).notifyRequestStatusChanged(
                request,
                "IN_PROGRESS",
                "RESCUED"
        );
    }

    @Test
    void completeRescueRejectsBlankNotes() {
        request.setStatus("IN_PROGRESS");
        assignment.setStatus("ACCEPTED");

        allowAssignmentAccess();
        allowActiveMembership();

        assertThatThrownBy(() ->
                rescuerService.completeRescue(
                        ASSIGNMENT_ID,
                        USER_ID,
                        "   "
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được để trống");

        verify(assignmentRepository, never())
                .save(assignment);

        verify(statusHistoryRepository, never())
                .save(
                        org.mockito.ArgumentMatchers
                                .any(StatusHistory.class)
                );
    }

    @Test
    void removedTeamMemberCannotChangeAssignment() {
        allowAssignmentAccess();

        when(teamMemberRepository.findActiveMembership(
                USER_ID,
                TEAM_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                rescuerService.startRescue(
                        ASSIGNMENT_ID,
                        USER_ID,
                        null
                )
        )
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("không còn thuộc đội");

        verify(assignmentRepository, never())
                .save(assignment);
verify(statusHistoryRepository, never())
                .save(
                        org.mockito.ArgumentMatchers
                                .any(StatusHistory.class)
                );
    }

    private void allowAssignmentAccess() {
        when(assignmentRepository.findByIdAndUserIdForUpdate(
                ASSIGNMENT_ID,
                USER_ID
        )).thenReturn(Optional.of(assignment));
    }

    private void allowActiveMembership() {
        when(teamMemberRepository.findActiveMembership(
                USER_ID,
                TEAM_ID
        )).thenReturn(Optional.of(membership));
    }
}
