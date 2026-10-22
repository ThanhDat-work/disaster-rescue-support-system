package vn.edu.hcmute.drss.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.drss.dtos.LoginRequest;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;

import java.util.List;
import java.util.Optional;

@Service
public class AccountLoginService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String STATUS_BANNED = "BANNED";

    /*
     * Hash BCrypt cost 10 dùng để cân bằng thời gian xử lý khi email
     * không tồn tại. Đây không phải mật khẩu hoặc secret thực tế.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$30qDd6yof/J3OeCsatpwpu6sANvnwSvL3UN1J.gwJ6WATtvTSMUU2";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimitService rateLimitService;
    private final JwtTokenService jwtTokenService;

    public AccountLoginService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            AuthRateLimitService rateLimitService,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimitService = rateLimitService;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResult login(
            LoginRequest request,
            String ipAddress
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Login request is required"
            );
        }

        RateLimitDecision initialDecision =
                rateLimitService.checkLoginAllowed(
                        request.email(),
                        ipAddress
                );

        if (!initialDecision.allowed()) {
            return LoginResult.rateLimited(
                    initialDecision.blockedUntil()
            );
        }

        Optional<User> userOptional =
                userRepository.findByEmailIgnoreCase(
                        request.email()
                );

        if (userOptional.isEmpty()) {
            passwordEncoder.matches(
                    safePassword(request.password()),
                    DUMMY_PASSWORD_HASH
            );

            return recordInvalidCredentials(
                    request.email(),
                    ipAddress
            );
        }

        User user = userOptional.get();

        boolean passwordMatches = passwordEncoder.matches(
                safePassword(request.password()),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            return recordInvalidCredentials(
                    request.email(),
                    ipAddress
            );
        }

        if (STATUS_INACTIVE.equals(user.getStatus())) {
            return LoginResult.inactive();
        }

        if (STATUS_BANNED.equals(user.getStatus())
                || !STATUS_ACTIVE.equals(user.getStatus())) {
            return LoginResult.banned();
        }

                List<String> activeRoles = ActiveRoleService.sortRoles(
                userRoleRepository
                        .findAllByUserAndRevokedAtIsNull(user)
                        .stream()
                        .map(UserRole::getRole)
                        .filter(role -> role != null)
                        .map(role -> role.getRoleName())
                        .filter(roleName ->
                                roleName != null
                                        && !roleName.isBlank()
                        )
                        .toList()
        );

        if (activeRoles.isEmpty()) {
            return LoginResult.noActiveRoles();
        }

        rateLimitService.clearLoginSubjectAfterSuccess(
                request.email(),
                ipAddress
        );

        if (activeRoles.contains(
                ActiveRoleService.ROLE_CITIZEN
        )) {
            String activeRole = ActiveRoleService.ROLE_CITIZEN;
            return LoginResult.authenticated(
                    jwtTokenService.issue(user, activeRole),
                    activeRole
            );
        }

        if (activeRoles.size() == 1) {
            String activeRole = activeRoles.get(0);
            return LoginResult.authenticated(
                    jwtTokenService.issue(user, activeRole),
                    activeRole
            );
        }

        return LoginResult.roleSelectionRequired(user.getId());
    }

    private LoginResult recordInvalidCredentials(
            String email,
            String ipAddress
    ) {
        RateLimitDecision failureDecision =
                rateLimitService.recordLoginFailure(
                        email,
                        ipAddress
                );

        if (!failureDecision.allowed()) {
            return LoginResult.rateLimited(
                    failureDecision.blockedUntil()
            );
        }

        return LoginResult.invalidCredentials();
    }

    private String safePassword(String password) {
        return password == null ? "" : password;
    }
}