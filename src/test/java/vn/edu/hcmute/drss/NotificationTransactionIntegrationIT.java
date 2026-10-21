package vn.edu.hcmute.drss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.hcmute.drss.entities.Notification;
import vn.edu.hcmute.drss.entities.NotificationEventType;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.realtime.RealtimeNotificationGateway;
import vn.edu.hcmute.drss.repositories.NotificationRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.services.BusinessNotificationService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("it")
class NotificationTransactionIntegrationIT {

    private static final UUID REQUEST_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666"
    );

    @Autowired
    private BusinessNotificationService notifications;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private RescueRequestRepository requestRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private RealtimeNotificationGateway gateway;

    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        clearInvocations(gateway);
    }

    @Test
    void commitPersistsRowAndEmitsOnlyAfterCommit() {
        long before = notificationRepository
                .countByReferenceIdAndEventType(
                        REQUEST_ID,
                        NotificationEventType.REQUEST_VERIFIED
                );
        AtomicReference<List<Notification>> created =
                new AtomicReference<>();

        transactions.executeWithoutResult(status -> {
            RescueRequest request = requestRepository
                    .findCoordinatorDetailById(REQUEST_ID)
                    .orElseThrow();
            created.set(notifications.notifyRequestStatusChanged(
                    request,
                    "PENDING",
                    "VERIFIED"
            ));
            verifyNoInteractions(gateway);
        });

        assertThat(notificationRepository
                .countByReferenceIdAndEventType(
                        REQUEST_ID,
                        NotificationEventType.REQUEST_VERIFIED
                )).isEqualTo(before + 1);
        verify(gateway).send(any());

        notificationRepository.deleteAll(created.get());
    }

    @Test
    void rollbackRemovesRowAndDoesNotEmit() {
        long before = notificationRepository
                .countByReferenceIdAndEventType(
                        REQUEST_ID,
                        NotificationEventType.REQUEST_REJECTED
                );

        assertThatThrownBy(() ->
                transactions.executeWithoutResult(status -> {
                    RescueRequest request = requestRepository
                            .findCoordinatorDetailById(REQUEST_ID)
                            .orElseThrow();
                    notifications.notifyRequestStatusChanged(
                            request,
                            "PENDING",
                            "REJECTED"
                    );
                    throw new IllegalStateException("force rollback");
                })
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("force rollback");

        assertThat(notificationRepository
                .countByReferenceIdAndEventType(
                        REQUEST_ID,
                        NotificationEventType.REQUEST_REJECTED
                )).isEqualTo(before);
        verifyNoInteractions(gateway);
    }
}