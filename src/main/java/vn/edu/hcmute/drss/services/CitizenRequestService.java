package vn.edu.hcmute.drss.services;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.MediaAsset;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.DisasterTypeRepository;
import vn.edu.hcmute.drss.repositories.MediaAssetRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@Service
public class CitizenRequestService {

    public static final int PAGE_SIZE = 10;
    static final int DISPLAY_CODE_ATTEMPTS = 10;
    static final int MAX_CANCEL_REASON_LENGTH = 2000;

    private static final Set<String> ALLOWED_URGENCIES =
            Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> CANCELLABLE_STATUSES =
            Set.of("PENDING", "VERIFIED");

    private final RescueRequestRepository requests;
    private final DisasterTypeRepository disasterTypes;
    private final StatusHistoryRepository histories;
    private final MediaAssetRepository media;
    private final UserRepository users;
    private final BusinessNotificationService notifications;

    public CitizenRequestService(
            RescueRequestRepository requests,
            DisasterTypeRepository disasterTypes,
            StatusHistoryRepository histories,
            MediaAssetRepository media,
            UserRepository users,
            BusinessNotificationService notifications
    ) {
        this.requests = requests;
        this.disasterTypes = disasterTypes;
        this.histories = histories;
        this.media = media;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<DisasterType> getActiveDisasterTypes() {
        return disasterTypes.findByIsActiveTrueOrderByDisasterNameAsc();
    }

    @Transactional
    public RescueRequest create(UUID userId, CreateCitizenRequest form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "D\u1eef li\u1ec7u y\u00eau c\u1ea7u kh\u00f4ng h\u1ee3p l\u1ec7."
            );
        }

        User user = users.findById(requireUserId(userId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Kh\u00f4ng t\u00ecm th\u1ea5y ng\u01b0\u1eddi d\u00f9ng."
                ));

        if (form.getDisasterTypeId() == null) {
            throw new IllegalArgumentException(
                    "Vui l\u00f2ng ch\u1ecdn lo\u1ea1i thi\u00ean tai."
            );
        }

        DisasterType type = disasterTypes.findById(form.getDisasterTypeId())
                .filter(item -> Boolean.TRUE.equals(item.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Lo\u1ea1i thi\u00ean tai kh\u00f4ng t\u1ed3n t\u1ea1i ho\u1eb7c \u0111\u00e3 ng\u1eebng ho\u1ea1t \u0111\u1ed9ng."
                ));

        RescueRequest request = new RescueRequest();
        request.setUser(user);
        request.setDisasterType(type);
        request.setUrgency(normalizeUrgency(form.getUrgency()));
        request.setStatus("PENDING");
        request.setAddress(trimToNull(form.getAddress()));
        request.setLatitude(form.getLatitude());
        request.setLongitude(form.getLongitude());
        request.setDescription(trimToNull(form.getDescription()));
        request.setDisplayCode(nextDisplayCode());
        return requests.save(request);
    }

    @Transactional(readOnly = true)
    public Page<RescueRequest> list(UUID userId, int requestedPage) {
        UUID ownerId = requireUserId(userId);
        int page = Math.max(0, requestedPage);
        Page<RescueRequest> result = findPage(ownerId, page);
        if (result.getTotalPages() > 0 && page >= result.getTotalPages()) {
            return findPage(ownerId, result.getTotalPages() - 1);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public RescueRequest detail(UUID requestId, UUID userId) {
        return ownedRequest(requestId, userId);
    }

    @Transactional(readOnly = true)
    public List<StatusHistory> history(UUID requestId, UUID userId) {
        ownedRequest(requestId, userId);
        return histories.findByRequest_IdOrderByCreatedAtAscIdAsc(requestId);
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> media(UUID requestId, UUID userId) {
        ownedRequest(requestId, userId);
        return media.findByRequest_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
                requestId
        );
    }

    @Transactional
    public void cancel(UUID requestId, UUID userId, String reason) {
        RescueRequest request = ownedRequest(requestId, userId);
        String oldStatus = request.getStatus();
        if (!CANCELLABLE_STATUSES.contains(oldStatus)) {
            throw new IllegalStateException(
                    "Ch\u1ec9 c\u00f3 th\u1ec3 h\u1ee7y y\u00eau c\u1ea7u \u1edf tr\u1ea1ng th\u00e1i PENDING ho\u1eb7c VERIFIED."
            );
        }

        String normalizedReason = requireCancelReason(reason);
        request.setStatus("CANCELLED");

        StatusHistory history = new StatusHistory();
        history.setRequest(request);
        history.setOldStatus(oldStatus);
        history.setNewStatus("CANCELLED");
        history.setChangedByUser(request.getUser());
        history.setNotes(normalizedReason);
        histories.save(history);
        requests.flush();
        notifications.notifyRequestStatusChanged(
                request,
                oldStatus,
                "CANCELLED"
        );
    }

    private Page<RescueRequest> findPage(UUID userId, int page) {
        return requests.findByUser_IdOrderByCreatedAtDescIdDesc(
                userId,
                PageRequest.of(page, PAGE_SIZE, Sort.unsorted())
        );
    }

    private RescueRequest ownedRequest(UUID requestId, UUID userId) {
        UUID ownerId = requireUserId(userId);
        if (requestId == null) {
            throw notFound();
        }

        RescueRequest request = requests.findWithDisasterTypeById(requestId)
                .orElseThrow(CitizenRequestService::notFound);
        if (request.getUser() == null
                || !ownerId.equals(request.getUser().getId())) {
            throw notFound();
        }
        return request;
    }

    private String nextDisplayCode() {
        for (int attempt = 0; attempt < DISPLAY_CODE_ATTEMPTS; attempt++) {
            String code = "REQ-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 16)
                    .toUpperCase(Locale.ROOT);
            if (!requests.existsByDisplayCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "Kh\u00f4ng th\u1ec3 t\u1ea1o m\u00e3 y\u00eau c\u1ea7u. Vui l\u00f2ng th\u1eed l\u1ea1i."
        );
    }

    private String normalizeUrgency(String urgency) {
        String normalized = urgency == null
                ? ""
                : urgency.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_URGENCIES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "M\u1ee9c \u0111\u1ed9 kh\u1ea9n c\u1ea5p kh\u00f4ng h\u1ee3p l\u1ec7."
            );
        }
        return normalized;
    }

    private String requireCancelReason(String reason) {
        String normalized = trimToNull(reason);
        if (normalized == null) {
            throw new IllegalArgumentException(
                    "Vui l\u00f2ng nh\u1eadp l\u00fd do h\u1ee7y."
            );
        }
        if (normalized.length() > MAX_CANCEL_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "L\u00fd do h\u1ee7y t\u1ed1i \u0111a 2.000 k\u00fd t\u1ef1."
            );
        }
        return normalized;
    }

    private UUID requireUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "Ng\u01b0\u1eddi d\u00f9ng ch\u01b0a x\u00e1c th\u1ef1c h\u1ee3p l\u1ec7."
            );
        }
        return userId;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static IllegalArgumentException notFound() {
        return new IllegalArgumentException(
                "Kh\u00f4ng t\u00ecm th\u1ea5y y\u00eau c\u1ea7u."
        );
    }
}
