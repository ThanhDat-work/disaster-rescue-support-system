package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.Notification;
import vn.edu.hcmute.drss.entities.NotificationEventType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}