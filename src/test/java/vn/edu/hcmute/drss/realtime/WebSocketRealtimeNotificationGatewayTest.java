package vn.edu.hcmute.drss.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import vn.edu.hcmute.drss.entities.NotificationEventType;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketRealtimeNotificationGatewayTest {

    @Mock
    private SimpMessagingTemplate messaging;

    private WebSocketRealtimeNotificationGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new WebSocketRealtimeNotificationGateway(messaging);
    }

    @Test
    void personalDeliveryUsesPrivateUserQueueAndSafePayload() {
        NotificationDelivery delivery = delivery(
                NotificationDestination.PERSONAL,
                null
        );

        gateway.send(delivery);

        ArgumentCaptor<RealtimeNotificationPayload> payload =
                ArgumentCaptor.forClass(
                        RealtimeNotificationPayload.class
                );

        verify(messaging).convertAndSendToUser(
                eq(delivery.recipientUserId().toString()),
                eq("/queue/notifications"),
                payload.capture()
        );

        assertThat(payload.getValue().notificationId())
                .isEqualTo(delivery.notificationId());
        assertThat(payload.getValue().eventType())
                .isEqualTo(delivery.eventType());
        assertThat(payload.getValue().referenceId())
                .isEqualTo(delivery.referenceId());
        assertThat(payload.getValue().title())
                .isEqualTo(delivery.title());
        assertThat(payload.getValue().message())
                .isEqualTo(delivery.message());
        assertThat(payload.getValue().createdAt())
                .isEqualTo(delivery.createdAt());
    }

    @Test
    void coordinatorDeliveryUsesPrivateCoordinatorQueue() {
        NotificationDelivery delivery = delivery(
                NotificationDestination.COORDINATOR,
                null
        );

        gateway.send(delivery);

        verify(messaging).convertAndSendToUser(
                eq(delivery.recipientUserId().toString()),
                eq("/queue/coordinator/notifications"),
                org.mockito.ArgumentMatchers.any(
                        RealtimeNotificationPayload.class
                )
        );
    }

    @Test
    void teamDeliveryUsesRecipientPrivateTeamQueue() {
        UUID teamId = UUID.randomUUID();
        NotificationDelivery delivery = delivery(
                NotificationDestination.TEAM,
                teamId
        );

        gateway.send(delivery);

        verify(messaging).convertAndSendToUser(
                eq(delivery.recipientUserId().toString()),
                eq("/queue/teams/" + teamId + "/notifications"),
                org.mockito.ArgumentMatchers.any(
                        RealtimeNotificationPayload.class
                )
        );
    }

    private NotificationDelivery delivery(
            NotificationDestination destination,
            UUID teamId
    ) {
        return new NotificationDelivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                destination,
                teamId,
                NotificationEventType.REQUEST_VERIFIED,
                UUID.randomUUID(),
                "Cập nhật yêu cầu",
                "Yêu cầu đã được cập nhật trạng thái.",
                LocalDateTime.now()
        );
    }
}