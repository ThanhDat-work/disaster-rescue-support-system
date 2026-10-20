package vn.edu.hcmute.drss.services;

import java.util.List;
import java.util.Locale;
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
    private final RescueRequestRepository requests;
    private final DisasterTypeRepository disasterTypes;
    private final StatusHistoryRepository histories;
    private final MediaAssetRepository media;
    private final UserRepository users;

    public CitizenRequestService(RescueRequestRepository requests, DisasterTypeRepository disasterTypes,
            StatusHistoryRepository histories, MediaAssetRepository media, UserRepository users) {
        this.requests = requests;
        this.disasterTypes = disasterTypes;
        this.histories = histories;
        this.media = media;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<DisasterType> getActiveDisasterTypes() {
        return disasterTypes.findByIsActiveTrueOrderByDisasterNameAsc();
    }

    @Transactional
    public RescueRequest create(UUID userId, CreateCitizenRequest form) {
        User user = users.findById(requireUserId(userId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));
        DisasterType type = disasterTypes.findById(form.getDisasterTypeId())
                .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Loại thiên tai không tồn tại hoặc đã ngừng hoạt động."));
        String urgency = form.getUrgency() == null ? "" : form.getUrgency().trim().toUpperCase(Locale.ROOT);
        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(urgency)) {
            throw new IllegalArgumentException("Mức độ khẩn cấp không hợp lệ.");
        }
        RescueRequest request = new RescueRequest();
        request.setUser(user);
        request.setDisasterType(type);
        request.setUrgency(urgency);
        request.setStatus("PENDING");
        request.setAddress(form.getAddress() == null ? null : form.getAddress().trim());
        request.setLatitude(form.getLatitude());
        request.setLongitude(form.getLongitude());
        request.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
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

    private Page<RescueRequest> findPage(UUID userId, int page) {
        return requests.findByUser_IdOrderByCreatedAtDescIdDesc(userId,
                PageRequest.of(page, PAGE_SIZE, Sort.unsorted()));
    }

    @Transactional(readOnly = true)
    public RescueRequest detail(UUID requestId, UUID userId) { return ownedRequest(requestId, userId); }

    @Transactional(readOnly = true)
    public List<StatusHistory> history(UUID requestId, UUID userId) {
        ownedRequest(requestId, userId);
        return histories.findByRequest_IdOrderByCreatedAtAscIdAsc(requestId);
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> media(UUID requestId, UUID userId) {
        ownedRequest(requestId, userId);
        return media.findByRequest_IdAndDeletedAtIsNullOrderByCreatedAtAsc(requestId);
    }

    @Transactional
    public void cancel(UUID requestId, UUID userId, String reason) {
        RescueRequest request = ownedRequest(requestId, userId);
        String oldStatus = request.getStatus();
        if (!List.of("PENDING", "VERIFIED").contains(oldStatus)) {
            throw new IllegalStateException("Chỉ có thể hủy yêu cầu ở trạng thái PENDING hoặc VERIFIED.");
        }
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Vui lòng nhập lý do hủy.");
        request.setStatus("CANCELLED");
        StatusHistory history = new StatusHistory();
        history.setRequest(request);
        history.setOldStatus(oldStatus);
        history.setNewStatus("CANCELLED");
        history.setChangedByUser(request.getUser());
        history.setNotes(reason.trim());
        histories.save(history);
        requests.flush();
    }

    private RescueRequest ownedRequest(UUID requestId, UUID userId) {
        UUID ownerId = requireUserId(userId);
        RescueRequest request = requests.findWithDisasterTypeById(requestId)
                .orElseThrow(CitizenRequestService::notFound);
        if (!ownerId.equals(request.getUser().getId())) throw notFound();
        return request;
    }

    private String nextDisplayCode() {
        String code;
        do {
            code = "REQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
        } while (requests.existsByDisplayCode(code));
        return code;
    }

    private UUID requireUserId(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("Người dùng chưa xác thực hợp lệ.");
        return userId;
    }

    private static IllegalArgumentException notFound() { return new IllegalArgumentException("Không tìm thấy yêu cầu."); }
}
