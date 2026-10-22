package vn.edu.hcmute.drss.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edu.hcmute.drss.events.NotificationBatchCreatedEvent;
import vn.edu.hcmute.drss.realtime.NotificationDelivery;
import vn.edu.hcmute.drss.realtime.RealtimeNotificationGateway;

@Component
public class NotificationAfterCommitListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            NotificationAfterCommitListener.class
    );

    private final RealtimeNotificationGateway gateway;

    public NotificationAfterCommitListener(
            RealtimeNotificationGateway gateway
    ) {
        this.gateway = gateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(NotificationBatchCreatedEvent event) {
        for (NotificationDelivery delivery : event.deliveries()) {
            try {
                gateway.send(delivery);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Realtime notification delivery failed after commit "
                                + "for notification {} and event {}",
                        delivery.notificationId(),
                        delivery.eventType()
                );
            }
        }
    }
}