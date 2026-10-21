package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.entities.NotificationEventType;
import vn.edu.hcmute.drss.events.NotificationBatchCreatedEvent;
import vn.edu.hcmute.drss.realtime.NotificationDelivery;
import vn.edu.hcmute.drss.realtime.NotificationDestination;
import vn.edu.hcmute.drss.realtime.RealtimeNotificationGateway;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationAfterCommitListenerTest {

    @Mock
    private RealtimeNotificationGateway gateway;

    @Test
    void gatewayFailureDoesNotEscapeAfterCommitListener() {
        NotificationDelivery first = delivery();
        NotificationDelivery second = delivery();
        doThrow(new IllegalStateException("broker unavailable"))
                .when(gateway).send(first);

        NotificationAfterCommitListener listener =
                new NotificationAfterCommitListener(gateway);

        assertThatCode(() -> listener.deliver(
                new NotificationBatchCreatedEvent(List.of(first, second))
        )).doesNotThrowAnyException();

        verify(gateway).send(first);
        verify(gateway).send(second);
    }

    private NotificationDelivery delivery() {
        return new NotificationDelivery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationDestination.PERSONAL,
                null,
                NotificationEventType.REQUEST_VERIFIED,
                UUID.randomUUID(),
                "Title",
                "Safe message",
                LocalDateTime.now()
        );
    }
}