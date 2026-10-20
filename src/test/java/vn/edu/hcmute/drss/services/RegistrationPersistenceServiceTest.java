package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.hcmute.drss.dtos.RegistrationRequest;
import vn.edu.hcmute.drss.entities.Role;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.repositories.RoleRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationPersistenceServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private VerificationTokenService tokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SecureRandom secureRandom;

    private RegistrationPersistenceService service;
    private Role citizenRole;

    @BeforeEach
    void setUp() {
        citizenRole = new Role();
        citizenRole.setId(3L);
        citizenRole.setRoleName("ROLE_CITIZEN");

        service = new RegistrationPersistenceService(
                userRepository,
                roleRepository,
                userRoleRepository,
                tokenService,
                passwordEncoder,
                secureRandom,
                Clock.fixed(
                        Instant.parse("2026-10-19T07:00:00Z"),
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    void createsInactiveCitizenAndIssuesActivationOtp() {
        RegistrationRequest request = validRequest();

        when(userRepository.findByEmailIgnoreCase(
                request.email()
        )).thenReturn(Optional.empty());

        when(userRepository.existsByDisplayCode(any()))
                .thenReturn(false);

        doAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(0);

            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) (index + 1);
            }

            return null;
        }).when(secureRandom).nextBytes(any(byte[].class));

        when(passwordEncoder.encode(request.password()))
                .thenReturn("bcrypt-hash");

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(USER_ID);
                    return user;
                });

        when(roleRepository.findByRoleName("ROLE_CITIZEN"))
                .thenReturn(Optional.of(citizenRole));

        when(userRoleRepository.findById(any()))
                .thenReturn(Optional.empty());

        when(tokenService.issue(
                any(User.class),
                any(VerificationTokenPurpose.class)
        )).thenReturn(
                new IssuedOtp(
                        "123456",
                        java.time.LocalDateTime.now().plusMinutes(5)
                )
        );

        Optional<PendingOtpDelivery> result =
                service.prepareRegistration(request);

        assertThat(result).isPresent();
        assertThat(result.get().recipient())
                .isEqualTo("citizen@example.com");
        assertThat(result.get().otp()).isEqualTo("123456");
        assertThat(result.get().toString())
                .doesNotContain(
                        "citizen@example.com",
                        "123456"
                );

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .saveAndFlush(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getDisplayCode())
                .isEqualTo("USR-010203040506");
        assertThat(savedUser.getFullName())
                .isEqualTo("Nguyen Van Dan");
        assertThat(savedUser.getEmail())
                .isEqualTo("citizen@example.com");
        assertThat(savedUser.getPhoneNumber())
                .isEqualTo("0912345678");
        assertThat(savedUser.getPasswordHash())
                .isEqualTo("bcrypt-hash");
        assertThat(savedUser.getStatus())
                .isEqualTo("INACTIVE");
        assertThat(savedUser.getAuthVersion()).isZero();

        ArgumentCaptor<UserRole> roleCaptor =
                ArgumentCaptor.forClass(UserRole.class);

        verify(userRoleRepository)
                .save(roleCaptor.capture());

        UserRole savedRole = roleCaptor.getValue();

        assertThat(savedRole.getUser()).isSameAs(savedUser);
        assertThat(savedRole.getRole()).isSameAs(citizenRole);
        assertThat(savedRole.getRevokedAt()).isNull();

        verify(tokenService).issue(
                savedUser,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION
        );
    }

    @Test
    void inactiveAccountIsUpdatedWithoutCreatingDuplicateUser() {
        RegistrationRequest request = validRequest();

        User inactiveUser = existingUser("INACTIVE");

        when(userRepository.findByEmailIgnoreCase(
                request.email()
        )).thenReturn(Optional.of(inactiveUser));

        when(passwordEncoder.encode(request.password()))
                .thenReturn("new-bcrypt-hash");

        when(userRepository.saveAndFlush(inactiveUser))
                .thenReturn(inactiveUser);

        when(roleRepository.findByRoleName("ROLE_CITIZEN"))
                .thenReturn(Optional.of(citizenRole));

        when(userRoleRepository.findById(any()))
                .thenReturn(Optional.empty());

        when(tokenService.issue(
                inactiveUser,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION
        )).thenReturn(
                new IssuedOtp(
                        "654321",
                        java.time.LocalDateTime.now().plusMinutes(5)
                )
        );

        Optional<PendingOtpDelivery> result =
                service.prepareRegistration(request);

        assertThat(result).isPresent();
        assertThat(inactiveUser.getFullName())
                .isEqualTo("Nguyen Van Dan");
        assertThat(inactiveUser.getPhoneNumber())
                .isEqualTo("0912345678");
        assertThat(inactiveUser.getPasswordHash())
                .isEqualTo("new-bcrypt-hash");
        assertThat(inactiveUser.getDisplayCode())
                .isEqualTo("USR-EXISTING01");

        verify(userRepository, never())
                .existsByDisplayCode(any());
    }

    @Test
    void activeAccountReturnsGenericNoDeliveryResult() {
        RegistrationRequest request = validRequest();
        User activeUser = existingUser("ACTIVE");

        when(userRepository.findByEmailIgnoreCase(
                request.email()
        )).thenReturn(Optional.of(activeUser));

        Optional<PendingOtpDelivery> result =
                service.prepareRegistration(request);

        assertThat(result).isEmpty();

        verify(passwordEncoder, never()).encode(any());
        verify(roleRepository, never()).findByRoleName(any());
        verify(tokenService, never()).issue(
                any(),
                any()
        );
    }

    @Test
    void bannedAccountIsNotModified() {
        RegistrationRequest request = validRequest();
        User bannedUser = existingUser("BANNED");

        when(userRepository.findByEmailIgnoreCase(
                request.email()
        )).thenReturn(Optional.of(bannedUser));

        Optional<PendingOtpDelivery> result =
                service.prepareRegistration(request);

        assertThat(result).isEmpty();

        verify(userRepository, never()).saveAndFlush(any());
        verify(tokenService, never()).issue(any(), any());
    }

    private RegistrationRequest validRequest() {
        return new RegistrationRequest(
                " Nguyen Van Dan ",
                " Citizen@Example.com ",
                " 0912345678 ",
                "secure password 123",
                "secure password 123"
        );
    }

    private User existingUser(String status) {
        User user = new User();

        user.setId(USER_ID);
        user.setDisplayCode("USR-EXISTING01");
        user.setFullName("Old Name");
        user.setEmail("citizen@example.com");
        user.setPhoneNumber("0900000000");
        user.setPasswordHash("old-hash");
        user.setStatus(status);
        user.setAuthVersion(0);

        return user;
    }
}