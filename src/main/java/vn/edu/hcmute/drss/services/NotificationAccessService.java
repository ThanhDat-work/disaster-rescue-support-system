package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.NotificationInbox;
import vn.edu.hcmute.drss.dtos.NotificationItem;
import vn.edu.hcmute.drss.entities.Notification;
import vn.edu.hcmute.drss.exceptions.NotificationNotFoundException;
import vn.edu.hcmute.drss.repositories.NotificationRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class NotificationAccessService {

    private static final int MAX_SELECTED_NOTIFICATIONS = 50;

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationAccessService(
            NotificationRepository notifications,
            Clock clock
    ) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationInbox loadInbox(UUID recipientUserId) {
        UUID safeRecipientId = Objects.requireNonNull(
                recipientUserId,
                "Recipient user ID is required"
        );

        List<NotificationItem> items = notifications
                .findTop50ByRecipientUser_IdOrderByCreatedAtDescIdDesc(
                        safeRecipientId
                )
                .stream()
                .map(this::toItem)
                .toList();

        long unreadCount = notifications
                .countByRecipientUser_IdAndIsReadFalse(
                        safeRecipientId
                );

        return new NotificationInbox(items, unreadCount);
    }

    @Transactional
    public NotificationItem markRead(
            UUID notificationId,
            UUID recipientUserId
    ) {
        UUID safeNotificationId = Objects.requireNonNull(
                notificationId,
                "Notification ID is required"
        );
        UUID safeRecipientId = Objects.requireNonNull(
                recipientUserId,
                "Recipient user ID is required"
        );

        Notification notification = notifications
                .findByIdAndRecipientUser_Id(
                        safeNotificationId,
                        safeRecipientId
                )
                .orElseThrow(NotificationNotFoundException::new);

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now(clock));
            notification = notifications.saveAndFlush(notification);
        }

        return toItem(notification);
    }

    @Transactional
    public int markAllRead(UUID recipientUserId) {
        UUID safeRecipientId = Objects.requireNonNull(
                recipientUserId,
                "Recipient user ID is required"
        );
        return notifications.markAllUnreadAsRead(
                safeRecipientId,
                LocalDateTime.now(clock)
        );
    }

    @Transactional
    public int markSelectedRead(
            List<UUID> notificationIds,
            UUID recipientUserId
    ) {
        UUID safeRecipientId = Objects.requireNonNull(
                recipientUserId,
                "Recipient user ID is required"
        );

        if (notificationIds == null
                || notificationIds.size() > MAX_SELECTED_NOTIFICATIONS
                || notificationIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Notification ID list is invalid"
            );
        }

        List<UUID> safeIds = notificationIds.stream()
                .distinct()
                .toList();

        if (safeIds.isEmpty()) {
            return 0;
        }

        return notifications.markSelectedUnreadAsRead(
                safeRecipientId,
                safeIds,
                LocalDateTime.now(clock)
        );
    }

    private NotificationItem toItem(Notification notification) {
        return new NotificationItem(
                notification.getId(),
                notification.getEventType(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getTitle(),
                notification.getMessage(),
                Boolean.TRUE.equals(notification.getIsRead()),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}