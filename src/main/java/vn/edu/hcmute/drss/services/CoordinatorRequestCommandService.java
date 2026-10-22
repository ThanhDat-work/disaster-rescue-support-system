package vn.edu.hcmute.drss.services;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionAction;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionRequest;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@Service
public class CoordinatorRequestCommandService {

    public static final int MAX_REJECTION_REASON_LENGTH = 2000;

    private static final String PENDING = "PENDING";
    private static final String VERIFIED = "VERIFIED";
    private static final String REJECTED = "REJECTED";

    private final RescueRequestRepository requests;
    private final StatusHistoryRepository histories;
    private final UserRepository users;
    private final BusinessNotificationService notifications;

    public CoordinatorRequestCommandService(
            RescueRequestRepository requests,
            StatusHistoryRepository histories,
            UserRepository users,
            BusinessNotificationService notifications
    ) {
        this.requests = requests;
        this.histories = histories;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional
    public String decide(
            UUID requestId,
            UUID coordinatorId,
            CoordinatorDecisionRequest command
    ) {
        if (command == null || command.getAction() == null) {
            throw new IllegalArgumentException(
                    "Vui lòng chọn hành động xử lý."
            );
        }

        User coordinator = findCoordinator(coordinatorId);
        RescueRequest request = findRequest(requestId);

        verifyExpectedVersion(request, command.getVersion());
        verifyPendingState(request);

        String newStatus;
        String historyNotes;

        if (command.getAction() == CoordinatorDecisionAction.VERIFY) {
            newStatus = VERIFIED;
            historyNotes = "Điều phối viên đã xác minh yêu cầu.";
        } else {
            newStatus = REJECTED;
            historyNotes = requireRejectionReason(command.getReason());
        }

        request.setStatus(newStatus);

        StatusHistory history = new StatusHistory();
        history.setRequest(request);
        history.setOldStatus(PENDING);
        history.setNewStatus(newStatus);
        history.setChangedByUser(coordinator);
        history.setNotes(historyNotes);

        histories.save(history);

        // Flush trong transaction để optimistic-lock failure xuất hiện
        // trước khi controller trả response. Nếu flush thất bại, history
        // và status cùng rollback.
        requests.flush();

        notifications.notifyRequestStatusChanged(
                request,
                PENDING,
                newStatus
        );

        return newStatus;
    }

    private User findCoordinator(UUID coordinatorId) {
        if (coordinatorId == null) {
            throw new IllegalArgumentException(
                    "Người dùng chưa xác thực hợp lệ."
            );
        }

        return users.findById(coordinatorId)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy tài khoản điều phối viên."
                ));
    }

    private RescueRequest findRequest(UUID requestId) {
        if (requestId == null) {
            throw new CoordinatorRequestNotFoundException();
        }

        return requests.findByIdForCoordinatorAssignment(requestId)
                .orElseThrow(CoordinatorRequestNotFoundException::new);
    }

    private void verifyExpectedVersion(
            RescueRequest request,
            Long expectedVersion
    ) {
        if (expectedVersion == null
                || !Objects.equals(request.getVersion(), expectedVersion)) {
            throw new CoordinatorRequestConflictException(
                    "Yêu cầu vừa được cập nhật. Vui lòng tải lại trang."
            );
        }
    }

    private void verifyPendingState(RescueRequest request) {
        if (!PENDING.equals(request.getStatus())) {
            throw new CoordinatorRequestConflictException(
                    "Chỉ có thể xác minh hoặc từ chối yêu cầu đang ở trạng thái PENDING."
            );
        }
    }

    private String requireRejectionReason(String reason) {
        String normalized = trimToNull(reason);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "Vui lòng nhập lý do từ chối."
            );
        }

        if (normalized.length() > MAX_REJECTION_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Lý do từ chối tối đa 2.000 ký tự."
            );
        }

        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}