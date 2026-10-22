package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.ProfileEditRequest;
import vn.edu.hcmute.drss.dtos.ProfileView;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {
    private final UserRepository users;
    private final UserRoleRepository userRoles;

    public ProfileService(UserRepository users, UserRoleRepository userRoles) {
        this.users = users;
        this.userRoles = userRoles;
    }

    @Transactional(readOnly = true)
    public ProfileView view(UUID userId, String activeRole) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        List<String> granted = ActiveRoleService.sortRoles(userRoles
                .findAllByUserAndRevokedAtIsNull(user).stream()
                .map(UserRole::getRole)
                .map(role -> role.getRoleName())
                .toList());
        if (!granted.contains(activeRole)) {
            throw new IllegalStateException("Vai trò hiện tại không còn hiệu lực. Vui lòng đăng nhập lại.");
        }
        List<String> ordered = new ArrayList<>();
        ordered.add(activeRole);
        granted.stream().filter(role -> !role.equals(activeRole)).forEach(ordered::add);
        return new ProfileView(
                user.getDisplayCode(), user.getFullName(), user.getEmail(),
                user.getPhoneNumber(), user.getStatus(), activeRole,
                ordered, user.getCreatedAt(), user.getAvatarSecureUrl()
        );
    }

    @Transactional
    public void update(UUID userId, ProfileEditRequest form) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException("Tài khoản không thể chỉnh sửa hồ sơ.");
        }
        String name = form.getFullName().strip();
        String phone = form.getPhoneNumber().strip();
        if (name.isEmpty() || name.length() > 100 || !phone.matches("[0-9]{10,15}")) {
            throw new IllegalArgumentException("Thông tin hồ sơ không hợp lệ.");
        }
        user.setFullName(name);
        user.setPhoneNumber(phone);
        users.saveAndFlush(user);
    }
}