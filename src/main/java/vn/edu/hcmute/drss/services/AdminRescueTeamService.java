package vn.edu.hcmute.drss.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.AdminRescueTeamRequest;
import vn.edu.hcmute.drss.dtos.RescueTeamMemberView;
import vn.edu.hcmute.drss.dtos.RescueTeamView;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.entities.RescueTeamMember;
import vn.edu.hcmute.drss.entities.RescueTeamMemberId;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamRepository;
import vn.edu.hcmute.drss.repositories.TeamMemberCountProjection;
import vn.edu.hcmute.drss.repositories.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminRescueTeamService {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "AVAILABLE", "BUSY", "OFFLINE"
    );
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final String LEADER = "LEADER";
    private static final String MEMBER = "MEMBER";

    private final RescueTeamRepository rescueTeamRepository;
    private final RescueTeamMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Autowired
    public AdminRescueTeamService(
            RescueTeamRepository rescueTeamRepository,
            RescueTeamMemberRepository memberRepository,
            UserRepository userRepository
    ) {
        this.rescueTeamRepository = rescueTeamRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    /**
     * Constructor retained only so the pre-09.3 unit tests can instantiate
     * the legacy catalog surface. Spring always uses the annotated constructor.
     */
    public AdminRescueTeamService(RescueTeamRepository rescueTeamRepository) {
        this(rescueTeamRepository, null, null);
    }

    @Transactional(readOnly = true)
    public Page<RescueTeam> search(
            String keyword,
            String status,
            int page,
            int size
    ) {
        return rescueTeamRepository.searchAdminTeams(
                normalizeKeyword(keyword),
                normalizeOptionalStatus(status),
                pageRequest(page, size)
        );
    }

    @Transactional(readOnly = true)
    public RescueTeam get(UUID id) {
        return rescueTeamRepository.findById(id)
                .orElseThrow(this::teamNotFound);
    }

    @Transactional(readOnly = true)
    public List<User> eligibleCandidates() {
        requireMembershipInfrastructure();
        return userRepository.findEligibleRescuers();
    }

    @Transactional(readOnly = true)
    public RescueTeamView getTeamView(UUID teamId) {
        requireMembershipInfrastructure();
        RescueTeam team = rescueTeamRepository.findByIdWithLeader(teamId)
                .orElseThrow(this::teamNotFound);
        List<RescueTeamMemberView> members = memberRepository
                .findEligibleMembersByTeamId(teamId)
                .stream()
                .map(this::toMemberView)
                .toList();
        return toView(team, null, members);
    }

    @Transactional(readOnly = true)
    public List<RescueTeamView> findMembershipsForUser(UUID userId) {
        requireMembershipInfrastructure();
        List<RescueTeamMember> memberships = memberRepository
                .findEligibleMembershipsByUserId(userId);
        return summarizeMemberships(memberships);
    }

    @Transactional
    public RescueTeam create(AdminRescueTeamRequest request) {
        String code = normalizeCode(request.getCode());
        String name = normalizeRequiredText(request.getName(), "name");
        rejectDuplicateCode(code, null);
        rejectDuplicateName(name, null);

        if (request.isLegacyCompatibility()) {
            RescueTeam legacy = new RescueTeam();
            applyLegacy(legacy, request, code, name);
            return save(legacy);
        }

        requireMembershipInfrastructure();
        MembershipSelection selection = validateSelection(request);
        RescueTeam team = new RescueTeam();
        applyManaged(team, request, code, name, selection.leader());
        RescueTeam saved = save(team);
        syncMemberships(saved, selection);
        return saved;
    }

    @Transactional
    public RescueTeam update(UUID id, AdminRescueTeamRequest request) {
        String code = normalizeCode(request.getCode());
        String name = normalizeRequiredText(request.getName(), "name");
        rejectDuplicateCode(code, id);
        rejectDuplicateName(name, id);

        if (request.isLegacyCompatibility()) {
            RescueTeam legacy = get(id);
            applyLegacy(legacy, request, code, name);
            return save(legacy);
        }

        requireMembershipInfrastructure();
        RescueTeam team = rescueTeamRepository.findByIdForAdminUpdate(id)
                .orElseThrow(this::teamNotFound);
        MembershipSelection selection = validateSelection(request);
        applyManaged(team, request, code, name, selection.leader());
        RescueTeam saved = save(team);
        syncMemberships(saved, selection);
        return saved;
    }

    @Transactional
    public RescueTeam changeStatus(UUID id, String status) {
        RescueTeam team = get(id);
        team.setStatus(normalizeRequiredStatus(status));
        return save(team);
    }

    private MembershipSelection validateSelection(
            AdminRescueTeamRequest request
    ) {
        UUID leaderId = request.getLeaderUserId();
        if (leaderId == null) {
            throw new AdminDomainException(
                    "leaderUserId",
                    "Vui lòng chọn một đội trưởng có ROLE_RESCUER đang hoạt động"
            );
        }

        LinkedHashSet<UUID> memberIds = new LinkedHashSet<>(
                request.getMemberUserIds() == null
                        ? Set.of()
                        : request.getMemberUserIds()
        );
        memberIds.remove(null);
        if (memberIds.contains(leaderId)) {
            throw new AdminDomainException(
                    "memberUserIds",
                    "Một người không thể đồng thời là đội trưởng và thành viên trong cùng đội"
            );
        }

        LinkedHashSet<UUID> allIds = new LinkedHashSet<>(memberIds);
        allIds.add(leaderId);
        List<User> eligible = userRepository
                .findEligibleRescuersByIdInForShare(allIds);
        Map<UUID, User> byId = eligible.stream().collect(Collectors.toMap(
                User::getId,
                Function.identity()
        ));

        if (byId.size() != allIds.size()) {
            throw new AdminDomainException(
                    "memberUserIds",
                    "Đội trưởng hoặc thành viên không còn ACTIVE/ROLE_RESCUER hợp lệ"
            );
        }

        List<User> members = memberIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return new MembershipSelection(byId.get(leaderId), members);
    }

    private void syncMemberships(
            RescueTeam team,
            MembershipSelection selection
    ) {
        LocalDateTime now = LocalDateTime.now();
        Map<UUID, RescueTeamMember> existing = memberRepository
                .findAllByTeam_Id(team.getId())
                .stream()
                .collect(Collectors.toMap(
                        row -> row.getUser().getId(),
                        Function.identity()
                ));

        LinkedHashMap<UUID, MembershipTarget> wanted = new LinkedHashMap<>();
        wanted.put(
                selection.leader().getId(),
                new MembershipTarget(selection.leader(), LEADER)
        );
        for (User member : selection.members()) {
            wanted.put(member.getId(), new MembershipTarget(member, MEMBER));
        }

        List<RescueTeamMember> changed = new ArrayList<>();
        for (RescueTeamMember row : existing.values()) {
            if (!wanted.containsKey(row.getUser().getId())
                    && row.getLeftAt() == null) {
                row.setLeftAt(now);
                changed.add(row);
            }
        }

        for (Map.Entry<UUID, MembershipTarget> entry : wanted.entrySet()) {
            MembershipTarget target = entry.getValue();
            RescueTeamMember row = existing.get(entry.getKey());
            if (row == null) {
                row = new RescueTeamMember();
                row.setId(new RescueTeamMemberId(team.getId(), entry.getKey()));
                row.setTeam(team);
                row.setUser(target.user());
                row.setJoinedAt(now);
            } else if (row.getLeftAt() != null) {
                row.setJoinedAt(now);
            }
            row.setRole(target.role());
            row.setLeftAt(null);
            changed.add(row);
        }

        memberRepository.saveAllAndFlush(changed);
    }

    private void applyManaged(
            RescueTeam team,
            AdminRescueTeamRequest request,
            String code,
            String name,
            User leader
    ) {
        team.setDisplayCode(code);
        team.setTeamName(name);
        team.setLeaderUser(leader);
        team.setLeaderName(leader.getFullName());
        team.setContactNumber(normalizePhone(request.getContactNumber()));
        team.setStatus(normalizeRequiredStatus(request.getStatus()));
    }

    private void applyLegacy(
            RescueTeam team,
            AdminRescueTeamRequest request,
            String code,
            String name
    ) {
        team.setDisplayCode(code);
        team.setTeamName(name);
        team.setLeaderName(normalizeRequiredText(
                request.getLeaderName(),
                "leaderName"
        ));
        team.setContactNumber(normalizePhone(request.getContactNumber()));
        team.setStatus(normalizeRequiredStatus(request.getStatus()));
    }

    private List<RescueTeamView> summarizeMemberships(
            List<RescueTeamMember> memberships
    ) {
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<UUID> teamIds = memberships.stream()
                .map(row -> row.getTeam().getId())
                .distinct()
                .toList();
        Map<UUID, Long> counts = memberRepository
                .countEligibleMembersByTeamIds(teamIds)
                .stream()
                .collect(Collectors.toMap(
                        TeamMemberCountProjection::getTeamId,
                        TeamMemberCountProjection::getTotal
                ));
        return memberships.stream()
                .map(row -> toSummary(
                        row.getTeam(),
                        row.getRole(),
                        counts.getOrDefault(row.getTeam().getId(), 0L)
                ))
                .toList();
    }

    private RescueTeamView toSummary(
            RescueTeam team,
            String currentUserRole,
            long count
    ) {
        return new RescueTeamView(
                team.getId(),
                team.getDisplayCode(),
                team.getTeamName(),
                team.getContactNumber(),
                team.getStatus(),
                currentUserRole,
                toLeaderView(team),
                count,
                List.of()
        );
    }

    private RescueTeamView toView(
            RescueTeam team,
            String currentUserRole,
            List<RescueTeamMemberView> members
    ) {
        return new RescueTeamView(
                team.getId(),
                team.getDisplayCode(),
                team.getTeamName(),
                team.getContactNumber(),
                team.getStatus(),
                currentUserRole,
                members.stream()
                        .filter(row -> LEADER.equals(row.teamRole()))
                        .findFirst()
                        .orElseGet(() -> toLeaderView(team)),
                members.size(),
                members
        );
    }

    private RescueTeamMemberView toLeaderView(RescueTeam team) {
        User leader = team.getLeaderUser();
        if (leader == null) {
            return null;
        }
        return new RescueTeamMemberView(
                leader.getId(),
                leader.getDisplayCode(),
                leader.getFullName(),
                leader.getAvatarSecureUrl(),
                LEADER
        );
    }

    private RescueTeamMemberView toMemberView(RescueTeamMember row) {
        User user = row.getUser();
        return new RescueTeamMemberView(
                user.getId(),
                user.getDisplayCode(),
                user.getFullName(),
                user.getAvatarSecureUrl(),
                row.getRole()
        );
    }

    private RescueTeam save(RescueTeam team) {
        try {
            return rescueTeamRepository.saveAndFlush(team);
        } catch (DataIntegrityViolationException exception) {
            throw new AdminDomainException(
                    null,
                    "Mã hoặc tên đội cứu hộ đã tồn tại"
            );
        }
    }

    private void rejectDuplicateCode(String code, UUID excludedId) {
        boolean duplicate = excludedId == null
                ? rescueTeamRepository.existsByDisplayCodeIgnoreCase(code)
                : rescueTeamRepository.existsByDisplayCodeIgnoreCaseAndIdNot(
                        code, excludedId
                );
        if (duplicate) {
            throw new AdminDomainException("code", "Mã đội cứu hộ đã tồn tại");
        }
    }

    private void rejectDuplicateName(String name, UUID excludedId) {
        boolean duplicate = excludedId == null
                ? rescueTeamRepository.existsByTeamNameIgnoreCase(name)
                : rescueTeamRepository.existsByTeamNameIgnoreCaseAndIdNot(
                        name, excludedId
                );
        if (duplicate) {
            throw new AdminDomainException("name", "Tên đội cứu hộ đã tồn tại");
        }
    }

    private String normalizeCode(String value) {
        return normalizeRequiredText(value, "code").toUpperCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
        String phone = normalizeRequiredText(value, "contactNumber");
        if (!phone.matches("[0-9]{10,15}")) {
            throw new AdminDomainException(
                    "contactNumber",
                    "Số điện thoại phải gồm 10 đến 15 chữ số"
            );
        }
        return phone;
    }

    private String normalizeRequiredStatus(String value) {
        String status = normalizeRequiredText(value, "status")
                .toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new AdminDomainException(
                    "status",
                    "Trạng thái đội cứu hộ không hợp lệ"
            );
        }
        return status;
    }

    private String normalizeOptionalStatus(String value) {
        return value == null || value.isBlank()
                ? null
                : normalizeRequiredStatus(value);
    }

    private String normalizeRequiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AdminDomainException(
                    field,
                    "Giá trị bắt buộc không được để trống"
            );
        }
        return value.trim();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new AdminDomainException(
                    "keyword",
                    "Từ khóa tìm kiếm tối đa 100 ký tự"
            );
        }
        return normalized;
    }

    private Pageable pageRequest(int page, int size) {
        if (page < 0) {
            throw new AdminDomainException("page", "Số trang không hợp lệ");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new AdminDomainException(
                    "size",
                    "Kích thước trang phải từ 1 đến 100"
            );
        }
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.asc("displayCode"), Sort.Order.asc("id"))
        );
    }

    private void requireMembershipInfrastructure() {
        if (memberRepository == null || userRepository == null) {
            throw new IllegalStateException(
                    "Team membership infrastructure is unavailable"
            );
        }
    }

    private AdminDomainException teamNotFound() {
        return new AdminDomainException(null, "Không tìm thấy đội cứu hộ");
    }

    private record MembershipSelection(User leader, List<User> members) { }
    private record MembershipTarget(User user, String role) { }
}