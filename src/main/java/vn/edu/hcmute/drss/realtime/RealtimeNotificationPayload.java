package vn.edu.hcmute.drss.realtime;

import vn.edu.hcmute.drss.entities.NotificationEventType;

import java.time.LocalDateTime;
import java.util.UUID;

public record RealtimeNotificationPayload(
        UUID notificationId,
        NotificationEventType eventType,
        UUID referenceId,
        String title,
        String message,
        LocalDateTime createdAt
) {
    public static RealtimeNotificationPayload from(
            NotificationDelivery delivery
    ) {
        return new RealtimeNotificationPayload(
                delivery.notificationId(),
                delivery.eventType(),
                delivery.referenceId(),
                delivery.title(),
                delivery.message(),
                delivery.createdAt()
        );
    }
}