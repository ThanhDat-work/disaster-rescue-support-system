package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.Notification;
import vn.edu.hcmute.drss.entities.NotificationEventType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, UUID> {

    List<Notification>
            findTop50ByRecipientUser_IdOrderByCreatedAtDescIdDesc(
                    UUID recipientUserId
            );

    Optional<Notification> findByIdAndRecipientUser_Id(
            UUID notificationId,
            UUID recipientUserId
    );

    long countByRecipientUser_IdAndIsReadFalse(
            UUID recipientUserId
    );

    long countByReferenceIdAndEventType(
            UUID referenceId,
            NotificationEventType eventType
    );

    @Modifying
    @Query("""
            update Notification n
            set n.isRead = true, n.readAt = :readAt
            where n.recipientUser.id = :recipientUserId and n.isRead = false
            """)
    int markAllUnreadAsRead(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("readAt") LocalDateTime readAt
    );

    @Modifying
    @Query("""
            update Notification n
            set n.isRead = true, n.readAt = :readAt
            where n.recipientUser.id = :recipientUserId
              and n.id in :notificationIds
              and n.isRead = false
            """)
    int markSelectedUnreadAsRead(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("notificationIds") List<UUID> notificationIds,
            @Param("readAt") LocalDateTime readAt
    );
}
