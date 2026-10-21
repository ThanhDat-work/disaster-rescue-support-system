package vn.edu.hcmute.drss.dtos;

import java.util.List;

public record NotificationInbox(
        List<NotificationItem> items,
        long unreadCount
) {
    public NotificationInbox {
        items = List.copyOf(items);

        if (unreadCount < 0) {
            throw new IllegalArgumentException(
                    "Unread count must not be negative"
            );
        }
    }
}