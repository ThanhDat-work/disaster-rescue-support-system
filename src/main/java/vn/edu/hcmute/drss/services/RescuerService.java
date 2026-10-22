package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.RescueTeamMemberView;
import vn.edu.hcmute.drss.dtos.RescueTeamView;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.RescueTeamMember;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RescuerService {

    private static final String REQUEST_ASSIGNED = "ASSIGNED";
    private static final String REQUEST_IN_PROGRESS = "IN_PROGRESS";
    private static final String REQUEST_RESCUED = "RESCUED";

    private static final String ASSIGNMENT_ASSIGNED = "ASSIGNED";
    private static final String ASSIGNMENT_ACCEPTED = "ACCEPTED";
    private static final String ASSIGNMENT_COMPLETED = "COMPLETED";

    private final RescueAssignmentRepository assignmentRepository;
    private final RescueTeamMemberRepository teamMemberRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final BusinessNotificationService notifications;

    public RescuerService(
            RescueAssignmentRepository assignmentRepository,
            RescueTeamMemberRepository teamMemberRepository,
            StatusHistoryRepository statusHistoryRepository,
            BusinessNotificationService notifications
    ) {
        this.assignmentRepository = assignmentRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<RescueAssignment> getAssignmentsForUser(UUID userId) {
        requireUserId(userId);
        return assignmentRepository.findAssignmentsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<RescueTeamView> getMyTeams(UUID userId) {
        requireUserId(userId);
        List<RescueTeamMember> memberships = teamMemberRepository
                .findEligibleMembershipsByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<UUID> teamIds = memberships.stream()
                .map(row -> row.getTeam().getId())
                .distinct()
                .toList();
        Map<UUID, Long> counts = teamMemberRepository
                .countEligibleMembersByTeamIds(teamIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getTeamId(),
                        row -> row.getTotal()
                ));
        return memberships.stream()
                .map(row -> new RescueTeamView(
                        row.getTeam().getId(),
                        row.getTeam().getDisplayCode(),
                        row.getTeam().getTeamName(),
                        row.getTeam().getContactNumber(),
                        row.getTeam().getStatus(),
                        row.getRole(),
                        leaderView(row.getTeam()),
                        counts.getOrDefault(row.getTeam().getId(), 0L),
                        List.of()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public RescueTeamView getMyTeam(UUID teamId, UUID userId) {
        requireUserId(userId);
        RescueTeamMember membership = teamMemberRepository
                .findActiveMembership(userId, teamId)
                .orElseThrow(() -> new SecurityException(
                        "Bạn không còn thuộc đội cứu hộ này."
                ));
        List<RescueTeamMemberView> members = teamMemberRepository
                .findEligibleMembersByTeamId(teamId)
                .stream()
                .map(this::memberView)
                .toList();
        return new RescueTeamView(
                membership.getTeam().getId(),
                membership.getTeam().getDisplayCode(),
                membership.getTeam().getTeamName(),
                membership.getTeam().getContactNumber(),
                membership.getTeam().getStatus(),
                membership.getRole(),
                members.stream()
                        .filter(row -> "LEADER".equals(row.teamRole()))
                        .findFirst()
                        .orElseGet(() -> leaderView(membership.getTeam())),
                members.size(),
                members
        );
    }

    @Transactional(readOnly = true)
    public RescueAssignment getAssignmentDetail(
            UUID assignmentId,
            UUID userId
    ) {
        return requireAccessibleAssignment(assignmentId, userId, false);
    }

    @Transactional
    public void startRescue(
            UUID assignmentId,
            UUID userId,
            String notes
    ) {
        RescueAssignment assignment =
                requireAccessibleAssignment(assignmentId, userId, true);

        RescueTeamMember membership =
                requireActiveMembership(assignment, userId);

        RescueRequest request = assignment.getRequest();

        if (!REQUEST_ASSIGNED.equals(request.getStatus())
                || !ASSIGNMENT_ASSIGNED.equals(assignment.getStatus())) {
            throw new IllegalStateException(
                    "Chỉ có thể bắt đầu nhiệm vụ đang ở trạng thái ASSIGNED."
            );
        }

        request.setStatus(REQUEST_IN_PROGRESS);
        assignment.setStatus(ASSIGNMENT_ACCEPTED);

        StatusHistory history = createHistory(
request,
                membership,
                REQUEST_ASSIGNED,
                REQUEST_IN_PROGRESS,
                normalizeOptionalNotes(
                        notes,
                        "Đội cứu hộ bắt đầu thực hiện nhiệm vụ."
                )
        );

        assignmentRepository.save(assignment);
        statusHistoryRepository.save(history);
        assignmentRepository.flush();
        notifications.notifyRequestStatusChanged(
                request,
                REQUEST_ASSIGNED,
                REQUEST_IN_PROGRESS
        );
    }

    @Transactional
    public void completeRescue(
            UUID assignmentId,
            UUID userId,
            String notes
    ) {
        RescueAssignment assignment =
                requireAccessibleAssignment(assignmentId, userId, true);

        RescueTeamMember membership =
                requireActiveMembership(assignment, userId);

        RescueRequest request = assignment.getRequest();
        String normalizedNotes = requireCompletionNotes(notes);

        if (!REQUEST_IN_PROGRESS.equals(request.getStatus())
                || !ASSIGNMENT_ACCEPTED.equals(assignment.getStatus())) {
            throw new IllegalStateException(
                    "Chỉ có thể hoàn tất nhiệm vụ đang được thực hiện."
            );
        }

        LocalDateTime completedAt = LocalDateTime.now();

        request.setStatus(REQUEST_RESCUED);
        assignment.setStatus(ASSIGNMENT_COMPLETED);
        assignment.setCompletionTime(completedAt);
        assignment.setNotes(normalizedNotes);

        StatusHistory history = createHistory(
                request,
                membership,
                REQUEST_IN_PROGRESS,
                REQUEST_RESCUED,
                normalizedNotes
        );

        assignmentRepository.save(assignment);
        statusHistoryRepository.save(history);
        assignmentRepository.flush();
        notifications.notifyRequestStatusChanged(
                request,
                REQUEST_IN_PROGRESS,
                REQUEST_RESCUED
        );
    }

    private RescueAssignment requireAccessibleAssignment(
            UUID assignmentId,
            UUID userId,
            boolean forUpdate
    ) {
        if (assignmentId == null) {
            throw new IllegalArgumentException(
                    "Mã phân công không được để trống."
            );
        }

        requireUserId(userId);

        return (forUpdate
                ? assignmentRepository.findByIdAndUserIdForUpdate(
                        assignmentId,
                        userId
                )
                : assignmentRepository.findByIdAndUserId(
                        assignmentId,
                        userId
                ))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy nhiệm vụ hoặc bạn không có quyền "
                                + "truy cập nhiệm vụ này."
                ));
    }

    private RescueTeamMember requireActiveMembership(
            RescueAssignment assignment,
            UUID userId
    ) {
        return teamMemberRepository.findActiveMembership(
                        userId,
                        assignment.getTeam().getId()
                )
                .orElseThrow(() -> new SecurityException(
                        "Bạn không còn thuộc đội cứu hộ được phân công."
                ));
    }

    private StatusHistory createHistory(
            RescueRequest request,
RescueTeamMember membership,
            String oldStatus,
            String newStatus,
            String notes
    ) {
        StatusHistory history = new StatusHistory();
        history.setRequest(request);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedByUser(membership.getUser());
        history.setActorRole("ROLE_RESCUER");
        history.setNotes(notes);
        return history;
    }

    private RescueTeamMemberView memberView(RescueTeamMember membership) {
        return new RescueTeamMemberView(
                membership.getUser().getId(),
                membership.getUser().getDisplayCode(),
                membership.getUser().getFullName(),
                membership.getUser().getAvatarSecureUrl(),
                membership.getRole()
        );
    }

    private RescueTeamMemberView leaderView(
            vn.edu.hcmute.drss.entities.RescueTeam team
    ) {
        if (team.getLeaderUser() == null) {
            return null;
        }
        return new RescueTeamMemberView(
                team.getLeaderUser().getId(),
                team.getLeaderUser().getDisplayCode(),
                team.getLeaderUser().getFullName(),
                team.getLeaderUser().getAvatarSecureUrl(),
                "LEADER"
        );
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "Người dùng xác thực không hợp lệ."
            );
        }
    }

    private String normalizeOptionalNotes(
            String notes,
            String defaultValue
    ) {
        if (notes == null || notes.isBlank()) {
            return defaultValue;
        }

        return notes.trim();
    }

    private String requireCompletionNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException(
                    "Ghi chú hoàn tất không được để trống."
            );
        }

        return notes.trim();
    }
}