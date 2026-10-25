package vn.edu.hcmute.drss.events;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import vn.edu.hcmute.drss.realtime.NotificationDelivery;
import vn.edu.hcmute.drss.trace.RequestCorrelation;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationBatchCreatedEventTest {

    @Test
    void snapshotsRequestIdAtEventCreation() {
        String id = UUID.randomUUID().toString();
        NotificationBatchCreatedEvent event;
        try (MDC.MDCCloseable ignored = MDC.putCloseable(
                RequestCorrelation.KEY, id)) {
            event = new NotificationBatchCreatedEvent(
                    List.of(mock(NotificationDelivery.class)));
        }
        assertThat(event.requestId()).isEqualTo(id);
        assertThat(RequestCorrelation.currentId()).isNull();
    }
}
