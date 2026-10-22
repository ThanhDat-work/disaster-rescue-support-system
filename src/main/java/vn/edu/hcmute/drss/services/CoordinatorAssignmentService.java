package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.CoordinatorAssignmentRequest;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@Service
public class CoordinatorAssignmentService {

    public static final int MAX_COORDINATOR_NOTE_LENGTH = 2000;

    private static final String VERIFIED = "VERIFIED";
    private static final String ASSIGNED = "ASSIGNED";
    private static final String TEAM_AVAILABLE = "AVAILABLE";

    private static final Set<String> ACTIVE_ASSIGNMENT_STATUSES = Set.of(
            "ASSIGNED",
            "ACCEPTED"
    );

    private final RescueRequestRepository requests;
    private final RescueTeamRepository teams;
    private final RescueTeamMemberRepository teamMembers;
    private final RescueAssignmentRepository assignments;
    private final StatusHistoryRepository histories;
    private final UserRepository users;
    private final BusinessNotificationService notifications;

    public CoordinatorAssignmentService(
            RescueRequestRepository requests,
            RescueTeamRepository teams,
            RescueTeamMemberRepository teamMembers,
            RescueAssignmentRepository assignments,
            StatusHistoryRepository histories,
            UserRepository users,
            BusinessNotificationService notifications
    ) {
        this.requests = requests;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.assignments = assignments;
        this.histories = histories;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<RescueTeam> findAvailableTeams(UUID requestOwnerId) {
        if (requestOwnerId == null) {
            return List.of();
        }
        return List.copyOf(
                teams.findAvailableWithActiveMembersForAssignmentExcludingOwner(
                        requestOwnerId
                )
        );
    }

    @Transactional
    public RescueAssignment assign(
            UUID requestId,
            UUID coordinatorId,
            CoordinatorAssignmentRequest command
    ) {
        validateCommand(command);

        User coordinator = findCoordinator(coordinatorId);

        // Khóa request trước. Hai thao tác phân công cùng request sẽ được
        // tuần tự hóa; transaction đến sau phải đọc lại trạng thái mới.
        RescueRequest request = requests
                .findByIdForCoordinatorAction(requestId, coordinatorId)
                .orElseThrow(CoordinatorRequestNotFoundException::new);

        verifyExpectedVersion(request, command.getVersion());
        verifyVerifiedState(request);
        verifyNoActiveAssignment(request.getId());

        // Khóa team để việc đổi trạng thái team và phân công không nhìn thấy
        // dữ liệu nửa chừng của nhau.
        RescueTeam team = teams
                .findByIdForCoordinatorAssignment(command.getTeamId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy đội cứu hộ đã chọn."
                ));

        verifyAvailableTeam(team);
        verifyActiveMember(team.getId());
        verifyOwnerIsNotActiveTeamMember(request, team);

        LocalDateTime assignedAt = LocalDateTime.now();

        RescueAssignment assignment = new RescueAssignment();
        assignment.setDisplayCode(generateAssignmentCode());
        assignment.setRequest(request);
        assignment.setTeam(team);
        assignment.setStatus(ASSIGNED);
        assignment.setAssignedAt(assignedAt);
        assignment.setCompletionTime(null);
        assignment.setNotes(null);
        assignment.setVersion(0L);

        request.setStatus(ASSIGNED);

        StatusHistory history = new StatusHistory();
        history.setRequest(request);
        history.setOldStatus(VERIFIED);
        history.setNewStatus(ASSIGNED);
        history.setChangedByUser(coordinator);
        history.setActorRole("ROLE_COORDINATOR");
        history.setNotes(buildHistoryNote(
                team,
                command.getCoordinatorNote()
        ));

        RescueAssignment savedAssignment = assignments.save(assignment);
        histories.save(history);

        // Flush toàn persistence context khi transaction còn mở. Nếu có lỗi
        // constraint/locking, request, assignment và history cùng rollback.
        // Dùng entity do save(...) trả về vì merge có thể tạo managed instance khác.
        requests.flush();

        notifications.notifyAssignmentCreated(savedAssignment);
        notifications.notifyRequestStatusChanged(
                request,
                VERIFIED,
                ASSIGNED
        );

        return savedAssignment;
    }

    private void validateCommand(CoordinatorAssignmentRequest command) {
        if (command == null || command.getTeamId() == null) {
            throw new IllegalArgumentException(
                    "Vui lòng chọn đội cứu hộ."
            );
        }

        String note = trimToNull(command.getCoordinatorNote());
        if (note != null
                && note.length() > MAX_COORDINATOR_NOTE_LENGTH) {
            throw new IllegalArgumentException(
                    "Ghi chú phân công tối đa 2.000 ký tự."
            );
        }
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

    private void verifyVerifiedState(RescueRequest request) {
        if (!VERIFIED.equals(request.getStatus())) {
            throw new CoordinatorRequestConflictException(
                    "Chỉ có thể phân công yêu cầu đang ở trạng thái VERIFIED."
            );
        }
    }

    private void verifyNoActiveAssignment(UUID requestId) {
        if (assignments.existsByRequest_IdAndStatusIn(
                requestId,
                ACTIVE_ASSIGNMENT_STATUSES
        )) {
            throw new CoordinatorRequestConflictException(
                    "Yêu cầu đã có phân công đang hoạt động."
            );
        }
    }

    private void verifyAvailableTeam(RescueTeam team) {
        if (!TEAM_AVAILABLE.equals(team.getStatus())) {
            throw new CoordinatorRequestConflictException(
                    "Đội cứu hộ đã chọn hiện không khả dụng."
            );
        }
    }

    private void verifyActiveMember(UUID teamId) {
        if (!teamMembers.existsEligibleRescuerByTeamId(teamId)) {
            throw new CoordinatorRequestConflictException(
                    "Đội cứu hộ đã chọn không có thành viên hoạt động."
            );
        }
    }

    private String generateAssignmentCode() {
        String hex = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase(Locale.ROOT);

        return "ASN-" + hex;
    }

    private String buildHistoryNote(
            RescueTeam team,
            String coordinatorNote
    ) {
        String normalizedNote = trimToNull(coordinatorNote);
        String prefix = "Đã phân công cho "
                + team.getTeamName()
                + " ("
                + team.getDisplayCode()
                + ").";

        return normalizedNote == null
                ? prefix
                : prefix + " Ghi chú: " + normalizedNote;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

        private void verifyOwnerIsNotActiveTeamMember(
            RescueRequest request,
            RescueTeam team
    ) {
        UUID ownerId = Objects.requireNonNull(
                request.getUser(),
                "Request owner is required"
        ).getId();

        if (teamMembers.existsByTeam_IdAndUser_IdAndLeftAtIsNull(
                team.getId(),
                ownerId
        )) {
            throw new CoordinatorRequestConflictException(
                    "Không thể phân công yêu cầu cho đội có người gửi yêu cầu."
            );
        }
    }
}