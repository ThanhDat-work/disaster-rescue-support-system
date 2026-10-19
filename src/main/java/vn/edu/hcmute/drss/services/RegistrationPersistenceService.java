package vn.edu.hcmute.drss.services;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.RegistrationRequest;
import vn.edu.hcmute.drss.entities.Role;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.entities.UserRoleId;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.repositories.RoleRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RegistrationPersistenceService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String STATUS_BANNED = "BANNED";
    private static final String CITIZEN_ROLE = "ROLE_CITIZEN";

    private static final int DISPLAY_CODE_RANDOM_BYTES = 6;
    private static final int DISPLAY_CODE_MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final VerificationTokenService verificationTokenService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public RegistrationPersistenceService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            VerificationTokenService verificationTokenService,
            PasswordEncoder passwordEncoder,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.verificationTokenService = verificationTokenService;
        this.passwordEncoder = passwordEncoder;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    /**
     * Transaction này phải hoàn tất trước khi Gmail được gọi.
     */
    @Transactional
    public Optional<PendingOtpDelivery> prepareRegistration(
            RegistrationRequest request
    ) {
        validateRequest(request);

        Optional<User> existingUser =
                userRepository.findByEmailIgnoreCase(
                        request.email()
                );

        if (existingUser.isPresent()
                && isProtectedExistingStatus(
                        existingUser.get().getStatus()
                )) {
            return Optional.empty();
        }

        User user = existingUser
                .map(existing -> updateInactiveUser(
                        existing,
                        request
                ))
                .orElseGet(() -> createInactiveUser(request));

        userRepository.saveAndFlush(user);

        Role citizenRole = roleRepository
                .findByRoleName(CITIZEN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Required role ROLE_CITIZEN is missing"
                ));

        ensureCitizenRole(user, citizenRole);

        IssuedOtp issuedOtp = verificationTokenService.issue(
                user,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION
        );

        return Optional.of(
                new PendingOtpDelivery(
                        user.getEmail(),
                        issuedOtp.code()
                )
        );
    }

    private User createInactiveUser(
            RegistrationRequest request
    ) {
        User user = new User();

        user.setDisplayCode(generateUniqueDisplayCode());
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setStatus(STATUS_INACTIVE);
        user.setAuthVersion(0);

        return user;
    }

    private User updateInactiveUser(
            User user,
            RegistrationRequest request
    ) {
        if (!STATUS_INACTIVE.equals(user.getStatus())) {
            throw new IllegalStateException(
                    "Only inactive accounts may be updated"
            );
        }

        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );

        return user;
    }

    private void ensureCitizenRole(
            User user,
            Role citizenRole
    ) {
        UserRoleId roleId = new UserRoleId(
                user.getId(),
                citizenRole.getId()
        );

        UserRole userRole = userRoleRepository
                .findById(roleId)
                .orElseGet(UserRole::new);

        userRole.setId(roleId);
        userRole.setUser(user);
        userRole.setRole(citizenRole);
        userRole.setAssignedAt(currentDateTime());
        userRole.setRevokedAt(null);

        userRoleRepository.save(userRole);
    }

    private String generateUniqueDisplayCode() {
        for (
                int attempt = 0;
                attempt < DISPLAY_CODE_MAX_ATTEMPTS;
                attempt++
        ) {
            byte[] randomBytes =
                    new byte[DISPLAY_CODE_RANDOM_BYTES];

            secureRandom.nextBytes(randomBytes);

            String candidate = "USR-"
                    + HexFormat.of()
                    .withUpperCase()
                    .formatHex(randomBytes);

            if (!userRepository.existsByDisplayCode(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Unable to generate a unique user display code"
        );
    }

    private boolean isProtectedExistingStatus(String status) {
        return STATUS_ACTIVE.equals(status)
                || STATUS_BANNED.equals(status);
    }

    private void validateRequest(
            RegistrationRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Registration request is required"
            );
        }

        if (request.fullName() == null
                || request.fullName().isBlank()
                || request.fullName().length() > 100) {
            throw new IllegalArgumentException(
                    "Full name is invalid"
            );
        }

        validateEmail(request.email());

        if (request.phoneNumber() == null
                || !request.phoneNumber()
                .matches("[0-9]{10,15}")) {
            throw new IllegalArgumentException(
                    "Phone number is invalid"
            );
        }

        validatePassword(request.password());

        if (!request.isPasswordConfirmed()) {
            throw new IllegalArgumentException(
                    "Password confirmation does not match"
            );
        }
    }

    private void validateEmail(String email) {
        if (email == null
                || email.isBlank()
                || email.length() > 150) {
            throw new IllegalArgumentException(
                    "Email is invalid"
            );
        }

        try {
            InternetAddress address =
                    new InternetAddress(email, true);

            address.validate();

            if (!email.equals(address.getAddress())) {
                throw new IllegalArgumentException(
                        "Email is invalid"
                );
            }
        } catch (AddressException exception) {
            throw new IllegalArgumentException(
                    "Email is invalid"
            );
        }
    }

    private void validatePassword(String password) {
        if (password == null
                || password.length() < 12
                || password.length() > 64) {
            throw new IllegalArgumentException(
                    "Password length is invalid"
            );
        }

        boolean containsLetter = password.codePoints()
                .anyMatch(Character::isLetter);

        boolean containsDigit = password.codePoints()
                .anyMatch(Character::isDigit);

        if (!containsLetter || !containsDigit) {
            throw new IllegalArgumentException(
                    "Password must contain a letter and a digit"
            );
        }
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneId.systemDefault()
        );
    }
}