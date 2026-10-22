package vn.edu.hcmute.drss.services;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.CoordinatorCloseRequest;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@Service
public class CoordinatorCloseService {

    private static final String RESCUED = "RESCUED";
    private static final String CLOSED = "CLOSED";
    private static final String COMPLETED = "COMPLETED";

    private final RescueRequestRepository requests;
    private final RescueAssignmentRepository assignments;
    private final StatusHistoryRepository histories;
    private final UserRepository users;
    private final BusinessNotificationService notifications;

    public CoordinatorCloseService(
            RescueRequestRepository requests,
            RescueAssignmentRepository assignments,
            StatusHistoryRepository histories,
            UserRepository users,
            BusinessNotificationService notifications
    ) {
        this.requests = requests;
        this.assignments = assignments;
        this.histories = histories;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional
    public RescueRequest close(
            UUID requestId,
            UUID coordinatorId,
            CoordinatorCloseRequest command
    ) {
        if (requestId == null || coordinatorId == null || command == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu đóng yêu cầu không hợp lệ."
            );
        }

        User coordinator = users.findById(coordinatorId)
                .orElseThrow(() -> new IllegalStateException(
                        "Không xác định được điều phối viên đang đăng nhập."
                ));

        RescueRequest request = requests
                .findByIdForCoordinatorAssignment(requestId)
                .orElseThrow(CoordinatorRequestNotFoundException::new);

        requireCurrentVersion(request, command.getVersion());
        if (!RESCUED.equals(request.getStatus())) {
            throw new CoordinatorRequestConflictException(
                    "Chỉ yêu cầu RESCUED mới được đóng."
            );
        }

        RescueAssignment assignment = assignments
                .findFirstByRequest_IdOrderByAssignedAtDescIdDesc(requestId)
                .orElseThrow(() -> new CoordinatorRequestConflictException(
                        "Yêu cầu chưa có phân công cứu hộ để đối chiếu."
                ));

        validateCompletion(assignment);
        String closeNote = normalizeCloseNote(command.getCloseNote());

        request.setStatus(CLOSED);

        StatusHistory history = new StatusHistory();
        history.setRequest(request);
        history.setOldStatus(RESCUED);
        history.setNewStatus(CLOSED);
        history.setChangedByUser(coordinator);
        history.setNotes(closeNote);
        histories.save(history);

        requests.flush();
        notifications.notifyRequestStatusChanged(
                request,
                RESCUED,
                CLOSED
        );
        return request;
    }

    private void requireCurrentVersion(
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

    private void validateCompletion(RescueAssignment assignment) {
        if (!COMPLETED.equals(assignment.getStatus())) {
            throw new CoordinatorRequestConflictException(
                    "Nhiệm vụ cứu hộ chưa ở trạng thái COMPLETED."
            );
        }
        if (assignment.getCompletionTime() == null) {
            throw new CoordinatorRequestConflictException(
                    "Nhiệm vụ chưa có thời gian hoàn thành."
            );
        }
        if (assignment.getNotes() == null
                || assignment.getNotes().isBlank()) {
            throw new CoordinatorRequestConflictException(
                    "Nhiệm vụ chưa có báo cáo hoàn thành."
            );
        }
    }

    private String normalizeCloseNote(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Vui lòng nhập ghi chú đóng yêu cầu."
            );
        }

        String normalized = value.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new IllegalArgumentException(
                    "Ghi chú đóng phải có từ 10 đến 500 ký tự."
            );
        }
        return normalized;
    }
}