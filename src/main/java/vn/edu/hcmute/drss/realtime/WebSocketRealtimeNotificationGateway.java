package vn.edu.hcmute.drss.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketRealtimeNotificationGateway
        implements RealtimeNotificationGateway {

    private final SimpMessagingTemplate messaging;

    public WebSocketRealtimeNotificationGateway(
            SimpMessagingTemplate messaging
    ) {
        this.messaging = messaging;
    }

    @Override
    public void send(NotificationDelivery delivery) {
        String destination = switch (delivery.destination()) {
            case PERSONAL -> "/queue/notifications";
            case COORDINATOR -> "/queue/coordinator/notifications";
            case TEAM -> "/queue/teams/"
                    + delivery.teamId()
                    + "/notifications";
        };

        messaging.convertAndSendToUser(
                delivery.recipientUserId().toString(),
                destination,
                RealtimeNotificationPayload.from(delivery)
        );
    }
}