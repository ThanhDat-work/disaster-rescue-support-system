package vn.edu.hcmute.drss.events;

import vn.edu.hcmute.drss.realtime.NotificationDelivery;

import java.util.List;
import java.util.Objects;

public record NotificationBatchCreatedEvent(
        List<NotificationDelivery> deliveries
) {
    public NotificationBatchCreatedEvent {
        deliveries = List.copyOf(
                Objects.requireNonNull(deliveries, "Deliveries are required")
        );

        if (deliveries.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one delivery is required"
            );
        }
    }
}