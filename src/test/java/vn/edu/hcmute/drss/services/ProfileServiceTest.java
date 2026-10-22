package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.dtos.ProfileEditRequest;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    @Mock private UserRepository users;
    @Mock private UserRoleRepository roles;
    @InjectMocks private ProfileService service;

    @Test
    void editChangesOnlyWhitelistedIdentityFields() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setStatus("ACTIVE");
        user.setEmail("owner@example.com");
        user.setDisplayCode("USR-001");
        user.setAuthVersion(7);
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(user));
        service.update(id, new ProfileEditRequest("  Tên mới  ", "0901234567"));
        assertThat(user.getFullName()).isEqualTo("Tên mới");
        assertThat(user.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(user.getEmail()).isEqualTo("owner@example.com");
        assertThat(user.getDisplayCode()).isEqualTo("USR-001");
        assertThat(user.getAuthVersion()).isEqualTo(7);
        verify(users).saveAndFlush(user);
    }

    @Test
    void inactiveAccountCannotEdit() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setStatus("INACTIVE");
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.update(id,
                new ProfileEditRequest("Name", "0901234567")))
                .isInstanceOf(IllegalStateException.class);
    }
}