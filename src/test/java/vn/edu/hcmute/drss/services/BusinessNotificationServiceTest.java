package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import vn.edu.hcmute.drss.entities.Notification;
import vn.edu.hcmute.drss.entities.NotificationEventType;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.events.NotificationBatchCreatedEvent;
import vn.edu.hcmute.drss.realtime.NotificationDestination;
import vn.edu.hcmute.drss.repositories.NotificationRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessNotificationServiceTest {

    @Mock
    private NotificationRepository notifications;
    @Mock
    private UserRoleRepository userRoles;
    @Mock
    private RescueTeamMemberRepository teamMembers;
    @Mock
    private ApplicationEventPublisher publisher;

    private BusinessNotificationService service;

    @BeforeEach
    void setUp() {
        service = new BusinessNotificationService(
                notifications,
                userRoles,
                teamMembers,
                publisher
        );
    }

    @Test
    void newRequestTargetsOnlyUniqueActiveCoordinators() {
        stubNotificationPersistence();
        User first = user("ACTIVE");
        User inactive = user("BANNED");
        RescueRequest request = request(first);
        when(userRoles.findActiveUsersByRoleName("ROLE_COORDINATOR"))
                .thenReturn(List.of(first, first, inactive));

        List<Notification> result = service.notifyNewRequest(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecipientUser()).isSameAs(first);
        assertThat(result.get(0).getEventType())
                .isEqualTo(NotificationEventType.REQUEST_CREATED);
        assertThat(result.get(0).getMessage())
                .contains(request.getDisplayCode())
                .doesNotContain("secret", "https://", "090");

        ArgumentCaptor<NotificationBatchCreatedEvent> event =
                ArgumentCaptor.forClass(NotificationBatchCreatedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().deliveries()).singleElement()
                .satisfies(delivery -> {
                    assertThat(delivery.destination())
                            .isEqualTo(NotificationDestination.COORDINATOR);
                    assertThat(delivery.recipientUserId())
                            .isEqualTo(first.getId());
                    assertThat(delivery.teamId()).isNull();
                });
    }

    @Test
    void assignmentTargetsOnlyCurrentActiveTeamMembers() {
        stubNotificationPersistence();
        User member = user("ACTIVE");
        RescueRequest request = request(user("ACTIVE"));
        RescueTeam team = new RescueTeam();
        team.setId(UUID.randomUUID());
        RescueAssignment assignment = new RescueAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setDisplayCode("ASN-TEST0000000001");
        assignment.setRequest(request);
        assignment.setTeam(team);
        when(teamMembers.findActiveUsersByTeamId(team.getId()))
                .thenReturn(List.of(member));

        service.notifyAssignmentCreated(assignment);

        ArgumentCaptor<NotificationBatchCreatedEvent> event =
                ArgumentCaptor.forClass(NotificationBatchCreatedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().deliveries()).singleElement()
                .satisfies(delivery -> {
                    assertThat(delivery.destination())
                            .isEqualTo(NotificationDestination.TEAM);
                    assertThat(delivery.teamId()).isEqualTo(team.getId());
                    assertThat(delivery.referenceId())
                            .isEqualTo(assignment.getId());
                });
    }

    @Test
    void inactiveOwnerGetsNoStatusNotificationOrEvent() {
        RescueRequest request = request(user("BANNED"));

        assertThat(service.notifyRequestStatusChanged(
                request,
                "PENDING",
                "REJECTED"
        )).isEmpty();

        verify(notifications, never()).saveAllAndFlush(any());
        verify(publisher, never()).publishEvent(any());
    }

    private void stubNotificationPersistence() {
        when(notifications.saveAllAndFlush(any()))
                .thenAnswer(invocation -> {
                    List<Notification> rows = invocation.getArgument(0);
                    rows.forEach(row -> {
                        row.setId(UUID.randomUUID());
                        row.setCreatedAt(LocalDateTime.now());
                    });
                    return rows;
                });
    }

    private User user(String status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setStatus(status);
        return user;
    }

    private RescueRequest request(User owner) {
        RescueRequest request = new RescueRequest();
        request.setId(UUID.randomUUID());
        request.setDisplayCode("REQ-TEST0000000001");
        request.setUser(owner);
        request.setAddress("Không được đưa vào payload");
        return request;
    }
}