package vn.edu.hcmute.drss.realtime;

import vn.edu.hcmute.drss.entities.NotificationEventType;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public record NotificationDelivery(
        UUID notificationId,
        UUID recipientUserId,
        NotificationDestination destination,
        UUID teamId,
        NotificationEventType eventType,
        UUID referenceId,
        String title,
        String message,
        LocalDateTime createdAt
) {
    public NotificationDelivery {
        Objects.requireNonNull(notificationId, "Notification ID is required");
        Objects.requireNonNull(recipientUserId, "Recipient ID is required");
        Objects.requireNonNull(destination, "Destination is required");
        Objects.requireNonNull(eventType, "Event type is required");
        Objects.requireNonNull(referenceId, "Reference ID is required");
        Objects.requireNonNull(createdAt, "Created time is required");

        title = requireText(title, "Title");
        message = requireText(message, "Message");

        if (destination == NotificationDestination.TEAM && teamId == null) {
            throw new IllegalArgumentException(
                    "Team ID is required for team delivery"
            );
        }

        if (destination != NotificationDestination.TEAM && teamId != null) {
            throw new IllegalArgumentException(
                    "Team ID is only valid for team delivery"
            );
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }
}