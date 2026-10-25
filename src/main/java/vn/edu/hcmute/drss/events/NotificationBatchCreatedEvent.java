package vn.edu.hcmute.drss.events;

import vn.edu.hcmute.drss.realtime.NotificationDelivery;
import vn.edu.hcmute.drss.trace.RequestCorrelation;

import java.util.List;
import java.util.Objects;

public record NotificationBatchCreatedEvent(
        List<NotificationDelivery> deliveries,
        String requestId
) {
    public NotificationBatchCreatedEvent(List<NotificationDelivery> deliveries) {
        this(deliveries, RequestCorrelation.currentId());
    }

    public NotificationBatchCreatedEvent {
        deliveries = List.copyOf(
                Objects.requireNonNull(deliveries, "Deliveries are required")
        );

        if (deliveries.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one delivery is required"
            );
        }
        if (requestId != null) {
            try {
                if (!java.util.UUID.fromString(requestId).toString()
                        .equals(requestId)) {
                    throw new IllegalArgumentException("Invalid request ID");
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid request ID");
            }
        }
    }
}
