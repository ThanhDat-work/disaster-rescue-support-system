package vn.edu.hcmute.drss.dtos;

import vn.edu.hcmute.drss.entities.NotificationEventType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationItem(
        UUID id,
        NotificationEventType eventType,
        String referenceType,
        UUID referenceId,
        String title,
        String message,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
}