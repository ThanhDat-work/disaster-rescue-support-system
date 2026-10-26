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
                LOGGER.info(
                        "notification_dispatched requestId={} notificationId={} event={}",
                        event.requestId(),
                        delivery.notificationId(),
                        delivery.eventType()
                );
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "notification_dispatch_failed requestId={} "
                                + "notificationId={} event={}",
                        event.requestId(),
                        delivery.notificationId(),
                        delivery.eventType()
                );
            }
        }
    }
}