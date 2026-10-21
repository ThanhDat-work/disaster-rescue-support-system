package vn.edu.hcmute.drss.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.AdminUserDetail;
import vn.edu.hcmute.drss.dtos.AdminUserSummary;
import vn.edu.hcmute.drss.entities.Role;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.entities.UserRoleId;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.repositories.RoleRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";
    private static final String BANNED = "BANNED";
    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private static final List<String> ALLOWED_ROLES = List.of(
            "ROLE_ADMIN",
            "ROLE_COORDINATOR",
            "ROLE_RESCUER",
            "ROLE_CITIZEN");

    private static final Set<String> ALLOWED_ROLE_SET = Set.copyOf(ALLOWED_ROLES);

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            ACTIVE,
            INACTIVE,
            BANNED);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public AdminUserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    public List<String> allowedRoles() {
        return ALLOWED_ROLES;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserSummary> search(
            String keyword,
            String status,
            String roleName,
            int page,
            int size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeOptionalStatus(status);
        String normalizedRole = normalizeOptionalRole(roleName);

        Page<User> users = userRepository.searchAdminUsers(
                normalizedKeyword,
                normalizedStatus,
                normalizedRole,
                pageRequest(page, size));

        Map<UUID, List<String>> rolesByUserId = loadRoleMap(
                users.getContent());

        return users.map(user -> new AdminUserSummary(
                user.getId(),
                user.getDisplayCode(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getStatus(),
                rolesByUserId.getOrDefault(user.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public AdminUserDetail getDetail(UUID userId) {
        User user = findUser(userId);
        return toDetail(user, activeRoleNames(user));
    }

    @Transactional
    public void changeStatus(
            UUID targetUserId,
            String requestedStatus,
            UUID actorUserId) {
        UUID actorId = requireUserId(actorUserId, "Người thực hiện");
        String newStatus = normalizeMutableStatus(requestedStatus);
        User user = findUserForUpdate(targetUserId);

        if (INACTIVE.equals(user.getStatus())) {
            throw new AdminDomainException(
                    "status",
                    "Tài khoản chưa kích hoạt phải được kích hoạt bằng OTP");
        }

        if (!ACTIVE.equals(user.getStatus())
                && !BANNED.equals(user.getStatus())) {
            throw new AdminDomainException(
                    "status",
                    "Trạng thái tài khoản hiện tại không hợp lệ");
        }

        if (user.getStatus().equals(newStatus)) {
            return;
        }

        if (user.getId().equals(actorId) && BANNED.equals(newStatus)) {
            throw new AdminDomainException(
                    "status",
                    "Bạn không thể tự vô hiệu hóa tài khoản của mình");
        }

        Set<String> currentRoles = activeRoleNames(user);

        if (ACTIVE.equals(user.getStatus())
                && BANNED.equals(newStatus)
                && currentRoles.contains(ADMIN_ROLE)) {
            requireAnotherActiveAdmin();
        }

        user.setStatus(newStatus);
        incrementAuthVersion(user);
        saveUser(user);
    }

    @Transactional
    public void updateRoles(
            UUID targetUserId,
            Collection<String> requestedRoles,
            UUID actorUserId) {
        UUID actorId = requireUserId(actorUserId, "Người thực hiện");
        LinkedHashSet<String> normalizedRoles = normalizeRoles(
                requestedRoles);

        User user = findUserForUpdate(targetUserId);
        List<UserRole> assignments = userRoleRepository.findAllByUser(user);

        TreeSet<String> currentRoles = assignments.stream()
                .filter(assignment -> assignment.getRevokedAt() == null)
                .map(UserRole::getRole)
                .filter(role -> role != null && role.getRoleName() != null)
                .map(Role::getRoleName)
                .collect(Collectors.toCollection(TreeSet::new));

        if (currentRoles.equals(new TreeSet<>(normalizedRoles))) {
            return;
        }

        boolean removesAdmin = currentRoles.contains(ADMIN_ROLE)
                && !normalizedRoles.contains(ADMIN_ROLE);

        if (user.getId().equals(actorId) && removesAdmin) {
            throw new AdminDomainException(
                    "roles",
                    "Bạn không thể tự thu hồi ROLE_ADMIN của mình");
        }

        if (ACTIVE.equals(user.getStatus()) && removesAdmin) {
            requireAnotherActiveAdmin();
        }

        Map<String, Role> requestedRoleEntities = roleRepository
                .findAllByRoleNameIn(normalizedRoles)
                .stream()
                .collect(Collectors.toMap(
                        Role::getRoleName,
                        Function.identity()));

        if (!requestedRoleEntities.keySet().containsAll(normalizedRoles)) {
            throw new AdminDomainException(
                    "roles",
                    "Định nghĩa vai trò của hệ thống chưa đầy đủ");
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, UserRole> assignmentsByRole = assignments.stream()
                .filter(assignment -> assignment.getRole() != null)
                .collect(Collectors.toMap(
                        assignment -> assignment.getRole().getRoleName(),
                        Function.identity()));

        for (UserRole assignment : assignments) {
            String roleName = assignment.getRole().getRoleName();

            if (!normalizedRoles.contains(roleName)
                    && assignment.getRevokedAt() == null) {
                assignment.setRevokedAt(now);
            }
        }

        for (String roleName : normalizedRoles) {
            UserRole existing = assignmentsByRole.get(roleName);

            if (existing != null) {
                if (existing.getRevokedAt() != null) {
                    existing.setAssignedAt(now);
                    existing.setRevokedAt(null);
                }
                continue;
            }

            Role role = requestedRoleEntities.get(roleName);
            UserRole assignment = new UserRole();
            assignment.setId(new UserRoleId(user.getId(), role.getId()));
            assignment.setUser(user);
            assignment.setRole(role);
            assignment.setAssignedAt(now);
            assignment.setRevokedAt(null);
            assignments.add(assignment);
        }

        incrementAuthVersion(user);

        try {
            userRoleRepository.saveAllAndFlush(assignments);
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AdminDomainException(
                    "roles",
                    "Không thể cập nhật vai trò do dữ liệu vừa thay đổi");
        }
    }

    private Map<UUID, List<String>> loadRoleMap(List<User> users) {
        if (users.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<String>> result = new HashMap<>();

        for (UserRole assignment : userRoleRepository
                .findAllByUserInAndRevokedAtIsNull(users)) {
            if (assignment.getUser() == null
                    || assignment.getRole() == null
                    || assignment.getRole().getRoleName() == null) {
                continue;
            }

            result.computeIfAbsent(
                    assignment.getUser().getId(),
                    ignored -> new ArrayList<>()).add(assignment.getRole().getRoleName());
        }

        result.replaceAll((ignored, roles) -> roles.stream()
                .distinct()
                .sorted()
                .toList());

        return result;
    }

    private AdminUserDetail toDetail(
            User user,
            Collection<String> roles) {
        return new AdminUserDetail(
                user.getId(),
                user.getDisplayCode(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getStatus(),
                roles.stream().sorted().toList());
    }

    private TreeSet<String> activeRoleNames(User user) {
        return userRoleRepository
                .findAllByUserAndRevokedAtIsNull(user)
                .stream()
                .map(UserRole::getRole)
                .filter(role -> role != null && role.getRoleName() != null)
                .map(Role::getRoleName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private void requireAnotherActiveAdmin() {
        long activeAdminCount = userRoleRepository
                .countByRole_RoleNameAndRevokedAtIsNullAndUser_Status(
                        ADMIN_ROLE,
                        ACTIVE);

        if (activeAdminCount <= 1) {
            throw new AdminDomainException(
                    null,
                    "Không thể vô hiệu hóa hoặc thu hồi quyền của quản trị viên hoạt động cuối cùng");
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(requireUserId(userId, "Người dùng"))
                .orElseThrow(() -> new AdminDomainException(
                        null,
                        "Không tìm thấy người dùng"));
    }

    private User findUserForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(
                requireUserId(userId, "Người dùng"))
                .orElseThrow(() -> new AdminDomainException(
                        null,
                        "Không tìm thấy người dùng"));
    }

    private UUID requireUserId(UUID value, String fieldName) {
        if (value == null) {
            throw new AdminDomainException(
                    null,
                    fieldName + " không hợp lệ");
        }
        return value;
    }

    private void incrementAuthVersion(User user) {
        if (user.getAuthVersion() == Integer.MAX_VALUE) {
            throw new AdminDomainException(
                    null,
                    "Không thể cập nhật phiên xác thực của người dùng");
        }
        user.setAuthVersion(user.getAuthVersion() + 1);
    }

    private void saveUser(User user) {
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AdminDomainException(
                    null,
                    "Không thể cập nhật người dùng do dữ liệu vừa thay đổi");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        String normalized = keyword.trim();

        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new AdminDomainException(
                    "keyword",
                    "Từ khóa tìm kiếm tối đa 100 ký tự");
        }

        return normalized;
    }

    private String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        String normalized = status.trim().toUpperCase(Locale.ROOT);

        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new AdminDomainException(
                    "status",
                    "Bộ lọc trạng thái không hợp lệ");
        }

        return normalized;
    }

    private String normalizeMutableStatus(String status) {
        String normalized = normalizeOptionalStatus(status);

        if (!ACTIVE.equals(normalized) && !BANNED.equals(normalized)) {
            throw new AdminDomainException(
                    "status",
                    "Admin chỉ được chuyển trạng thái giữa ACTIVE và BANNED");
        }

        return normalized;
    }

    private String normalizeOptionalRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }

        String normalized = roleName.trim().toUpperCase(Locale.ROOT);

        if (!ALLOWED_ROLE_SET.contains(normalized)) {
            throw new AdminDomainException(
                    "role",
                    "Bộ lọc vai trò không hợp lệ");
        }

        return normalized;
    }

    private LinkedHashSet<String> normalizeRoles(
            Collection<String> requestedRoles) {
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            throw new AdminDomainException(
                    "roles",
                    "Người dùng phải có ít nhất một vai trò");
        }

        LinkedHashSet<String> normalizedRoles = requestedRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (normalizedRoles.isEmpty()) {
            throw new AdminDomainException(
                    "roles",
                    "Người dùng phải có ít nhất một vai trò");
        }

        if (!ALLOWED_ROLE_SET.containsAll(normalizedRoles)) {
            throw new AdminDomainException(
                    "roles",
                    "Danh sách vai trò chứa giá trị không hợp lệ");
        }

        return normalizedRoles;
    }

    private Pageable pageRequest(int page, int size) {
        if (page < 0) {
            throw new AdminDomainException(
                    "page",
                    "Số trang không hợp lệ");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new AdminDomainException(
                    "size",
                    "Kích thước trang phải từ 1 đến 100");
        }

        return PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.asc("displayCode"),
                        Sort.Order.asc("id")));
    }
}