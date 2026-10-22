package vn.edu.hcmute.drss.services;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.Notification;
import vn.edu.hcmute.drss.entities.NotificationEventType;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.events.NotificationBatchCreatedEvent;
import vn.edu.hcmute.drss.realtime.NotificationDelivery;
import vn.edu.hcmute.drss.realtime.NotificationDestination;
import vn.edu.hcmute.drss.repositories.NotificationRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class BusinessNotificationService {

    private static final String ACTIVE = "ACTIVE";
    private static final String COORDINATOR = "ROLE_COORDINATOR";
    private static final String REQUEST_REFERENCE = "RESCUE_REQUEST";
    private static final String ASSIGNMENT_REFERENCE = "RESCUE_ASSIGNMENT";

    private final NotificationRepository notifications;
    private final UserRoleRepository userRoles;
    private final RescueTeamMemberRepository teamMembers;
    private final ApplicationEventPublisher eventPublisher;

    public BusinessNotificationService(
            NotificationRepository notifications,
            UserRoleRepository userRoles,
            RescueTeamMemberRepository teamMembers,
            ApplicationEventPublisher eventPublisher
    ) {
        this.notifications = notifications;
        this.userRoles = userRoles;
        this.teamMembers = teamMembers;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Notification> notifyNewRequest(RescueRequest request) {
        requireRequest(request);

        return persistAndPublish(
                userRoles.findActiveUsersByRoleName(COORDINATOR),
                NotificationDestination.COORDINATOR,
                null,
                NotificationEventType.REQUEST_CREATED,
                REQUEST_REFERENCE,
                request.getId(),
                "Yêu cầu cứu nạn mới",
                "Yêu cầu " + request.getDisplayCode()
                        + " đang chờ xác minh."
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Notification> notifyRequestStatusChanged(
            RescueRequest request,
            String oldStatus,
            String newStatus
    ) {
        requireRequest(request);
        String normalizedOld = requireText(oldStatus, "Old status");
        String normalizedNew = requireText(newStatus, "New status");
        User owner = Objects.requireNonNull(
                request.getUser(),
                "Request owner is required"
        );

        List<User> recipients = ACTIVE.equals(owner.getStatus())
                ? List.of(owner)
                : List.of();

        return persistAndPublish(
                recipients,
                NotificationDestination.PERSONAL,
                null,
                NotificationEventType.fromStatus(normalizedNew),
                REQUEST_REFERENCE,
                request.getId(),
                "Trạng thái yêu cầu đã thay đổi",
                "Yêu cầu " + request.getDisplayCode()
                        + " chuyển từ " + normalizedOld
                        + " sang " + normalizedNew + "."
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Notification> notifyAssignmentCreated(
            RescueAssignment assignment
    ) {
        Objects.requireNonNull(assignment, "Assignment is required");
        Objects.requireNonNull(assignment.getId(), "Assignment ID is required");
        RescueRequest request = Objects.requireNonNull(
                assignment.getRequest(),
                "Assignment request is required"
        );
        requireRequest(request);
        UUID teamId = Objects.requireNonNull(
                assignment.getTeam(),
                "Assignment team is required"
        ).getId();
        Objects.requireNonNull(teamId, "Team ID is required");

        return persistAndPublish(
                teamMembers.findActiveUsersByTeamId(teamId),
                NotificationDestination.TEAM,
                teamId,
                NotificationEventType.ASSIGNMENT_CREATED,
                ASSIGNMENT_REFERENCE,
                assignment.getId(),
                "Nhiệm vụ cứu hộ mới",
                "Đội của bạn được phân công nhiệm vụ "
                        + assignment.getDisplayCode()
                        + " cho yêu cầu "
                        + request.getDisplayCode()
                        + "."
        );
    }

    private List<Notification> persistAndPublish(
            List<User> candidates,
            NotificationDestination destination,
            UUID teamId,
            NotificationEventType eventType,
            String referenceType,
            UUID referenceId,
            String title,
            String message
    ) {
        List<User> recipients = uniqueActiveUsers(candidates);
        if (recipients.isEmpty()) {
            return List.of();
        }

        List<Notification> rows = recipients.stream()
                .map(user -> notification(
                        user,
                        eventType,
                        referenceType,
                        referenceId,
                        title,
                        message
                ))
                .toList();

        List<Notification> saved = notifications.saveAllAndFlush(rows);
        List<NotificationDelivery> deliveries = saved.stream()
                .map(row -> delivery(row, destination, teamId))
                .toList();

        eventPublisher.publishEvent(
                new NotificationBatchCreatedEvent(deliveries)
        );

        return List.copyOf(saved);
    }

    private List<User> uniqueActiveUsers(List<User> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        Map<UUID, User> unique = new LinkedHashMap<>();
        for (User user : candidates) {
            if (user == null
                    || user.getId() == null
                    || !ACTIVE.equals(user.getStatus())) {
                continue;
            }
            unique.putIfAbsent(user.getId(), user);
        }
        return new ArrayList<>(unique.values());
    }

    private Notification notification(
            User recipient,
            NotificationEventType eventType,
            String referenceType,
            UUID referenceId,
            String title,
            String message
    ) {
        Notification row = new Notification();
        row.setRecipientUser(recipient);
        row.setTitle(requireText(title, "Title"));
        row.setMessage(requireText(message, "Message"));
        row.setEventType(Objects.requireNonNull(eventType));
        row.setReferenceType(requireText(referenceType, "Reference type"));
        row.setReferenceId(Objects.requireNonNull(referenceId));
        row.setIsRead(false);
        row.setReadAt(null);
        return row;
    }

    private NotificationDelivery delivery(
            Notification row,
            NotificationDestination destination,
            UUID teamId
    ) {
        return new NotificationDelivery(
                row.getId(),
                row.getRecipientUser().getId(),
                destination,
                teamId,
                row.getEventType(),
                row.getReferenceId(),
                row.getTitle(),
                row.getMessage(),
                row.getCreatedAt()
        );
    }

    private void requireRequest(RescueRequest request) {
        Objects.requireNonNull(request, "Request is required");
        Objects.requireNonNull(request.getId(), "Request ID is required");
        requireText(request.getDisplayCode(), "Request display code");
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}