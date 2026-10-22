package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.dtos.NotificationInbox;
import vn.edu.hcmute.drss.dtos.NotificationItem;
import vn.edu.hcmute.drss.entities.Notification;
import vn.edu.hcmute.drss.entities.NotificationEventType;
import vn.edu.hcmute.drss.exceptions.NotificationNotFoundException;
import vn.edu.hcmute.drss.repositories.NotificationRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationAccessServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID NOTIFICATION_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );
    private static final Instant NOW = Instant.parse(
            "2026-10-22T01:30:00Z"
    );

    @Mock
    private NotificationRepository notifications;

    private NotificationAccessService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                NOW,
                ZoneId.of("Asia/Ho_Chi_Minh")
        );
        service = new NotificationAccessService(
                notifications,
                clock
        );
    }

    @Test
    void loadInboxUsesOnlyAuthenticatedRecipientAndUnreadCount() {
        Notification row = notification(false);

        when(notifications
                .findTop50ByRecipientUser_IdOrderByCreatedAtDescIdDesc(
                        USER_ID
                ))
                .thenReturn(List.of(row));
        when(notifications
                .countByRecipientUser_IdAndIsReadFalse(USER_ID))
                .thenReturn(1L);

        NotificationInbox result = service.loadInbox(USER_ID);

        assertThat(result.unreadCount()).isEqualTo(1L);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).id())
                .isEqualTo(NOTIFICATION_ID);
        assertThat(result.items().get(0).read()).isFalse();
    }

    @Test
    void markUnreadNotificationSetsReadTimeAndPersists() {
        Notification row = notification(false);

        when(notifications.findByIdAndRecipientUser_Id(
                NOTIFICATION_ID,
                USER_ID
        )).thenReturn(Optional.of(row));
        when(notifications.saveAndFlush(row)).thenReturn(row);

        NotificationItem result = service.markRead(
                NOTIFICATION_ID,
                USER_ID
        );

        assertThat(row.getIsRead()).isTrue();
        assertThat(row.getReadAt()).isEqualTo(
                LocalDateTime.ofInstant(
                        NOW,
                        ZoneId.of("Asia/Ho_Chi_Minh")
                )
        );
        assertThat(result.read()).isTrue();
        verify(notifications).saveAndFlush(row);
    }

    @Test
    void markReadIsIdempotentAndKeepsOriginalReadTime() {
        Notification row = notification(true);
        LocalDateTime originalReadAt = LocalDateTime.of(
                2026,
                10,
                21,
                20,
                0
        );
        row.setReadAt(originalReadAt);

        when(notifications.findByIdAndRecipientUser_Id(
                NOTIFICATION_ID,
                USER_ID
        )).thenReturn(Optional.of(row));

        NotificationItem result = service.markRead(
                NOTIFICATION_ID,
                USER_ID
        );

        assertThat(result.readAt()).isEqualTo(originalReadAt);
        verify(notifications, never()).saveAndFlush(row);
    }

    @Test
    void foreignOrMissingNotificationUsesSameNotFoundFailure() {
        when(notifications.findByIdAndRecipientUser_Id(
                NOTIFICATION_ID,
                USER_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(
                NOTIFICATION_ID,
                USER_ID
        )).isInstanceOf(NotificationNotFoundException.class)
                .hasMessage("Không tìm thấy thông báo.");

        verify(notifications, never()).saveAndFlush(
                org.mockito.ArgumentMatchers.any()
        );
    }

    private Notification notification(boolean read) {
        Notification row = new Notification();
        row.setId(NOTIFICATION_ID);
        row.setTitle("Cập nhật yêu cầu");
        row.setMessage("Yêu cầu đã được xác minh.");
        row.setEventType(NotificationEventType.REQUEST_VERIFIED);
        row.setReferenceType("RESCUE_REQUEST");
        row.setReferenceId(UUID.fromString(
                "66666666-6666-6666-6666-666666666666"
        ));
        row.setIsRead(read);
        row.setCreatedAt(LocalDateTime.of(
                2026,
                10,
                22,
                8,
                0
        ));
        return row;
    }
}