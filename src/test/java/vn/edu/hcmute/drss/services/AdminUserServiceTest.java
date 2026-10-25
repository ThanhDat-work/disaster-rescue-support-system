package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.edu.hcmute.drss.dtos.AdminUserSummary;
import vn.edu.hcmute.drss.entities.AdminUserAudit;
import vn.edu.hcmute.drss.entities.AdminUserAuditAction;
import vn.edu.hcmute.drss.entities.Role;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.entities.UserRoleId;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.repositories.RoleRepository;
import vn.edu.hcmute.drss.repositories.AdminUserAuditRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private AdminUserAuditRepository auditRepository;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                userRepository,
                roleRepository,
                userRoleRepository,
                auditRepository);
    }

    @Test
    void searchNormalizesFiltersAndReturnsSafeViewModel() {
        User user = user("ACTIVE", 0);
        Role citizen = role(3L, "ROLE_CITIZEN");

        when(userRepository.searchAdminUsers(
                eq("dan"),
                eq("ACTIVE"),
                eq("ROLE_CITIZEN"),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));

        when(userRoleRepository.findAllByUserInAndRevokedAtIsNull(
                List.of(user))).thenReturn(List.of(assignment(user, citizen, null)));

        var result = service.search(
                "  dan  ",
                "active",
                "role_citizen",
                0,
                20);

        assertThat(result.getContent())
                .extracting(AdminUserSummary::email)
                .containsExactly(user.getEmail());
        assertThat(result.getContent().get(0).roles())
                .containsExactly("ROLE_CITIZEN");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(userRepository).searchAdminUsers(
                eq("dan"),
                eq("ACTIVE"),
                eq("ROLE_CITIZEN"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void searchRejectsInvalidFiltersAndPagination() {
        assertThatThrownBy(() -> service.search(
                "x".repeat(101), null, null, 0, 20)).isInstanceOf(AdminDomainException.class);

        assertThatThrownBy(() -> service.search(
                null, "UNKNOWN", null, 0, 20)).isInstanceOf(AdminDomainException.class);

        assertThatThrownBy(() -> service.search(
                null, null, "ROLE_UNKNOWN", 0, 20)).isInstanceOf(AdminDomainException.class);

        assertThatThrownBy(() -> service.search(
                null, null, null, -1, 20)).isInstanceOf(AdminDomainException.class);

        assertThatThrownBy(() -> service.search(
                null, null, null, 0, 101)).isInstanceOf(AdminDomainException.class);
    }

    @Test
    void statusChangeRejectsInactiveSelfBanAndLastActiveAdmin() {
        UUID actorId = UUID.randomUUID();

        User inactive = user("INACTIVE", 0);
        when(userRepository.findByIdForUpdate(inactive.getId()))
                .thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.changeStatus(
                inactive.getId(), "ACTIVE", actorId)).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("OTP");

        User self = user("ACTIVE", 0);
        when(userRepository.findByIdForUpdate(self.getId()))
                .thenReturn(Optional.of(self));
        assertThatThrownBy(() -> service.changeStatus(
                self.getId(), "BANNED", self.getId())).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("chính mình");

        User lastAdmin = user("ACTIVE", 0);
        Role admin = role(1L, "ROLE_ADMIN");
        when(userRepository.findByIdForUpdate(lastAdmin.getId()))
                .thenReturn(Optional.of(lastAdmin));
        when(userRoleRepository.findAllByUserAndRevokedAtIsNull(lastAdmin))
                .thenReturn(List.of(assignment(lastAdmin, admin, null)));
        when(userRoleRepository
                .findActiveAssignmentsByRoleNameAndUserStatusForUpdate(
                        "ROLE_ADMIN",
                        "ACTIVE"))
                .thenReturn(List.of(assignment(lastAdmin, admin, null)));

        assertThatThrownBy(() -> service.changeStatus(
                lastAdmin.getId(), "BANNED", actorId)).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("cuối cùng");
    }

    @Test
    void adminCannotChangeOwnStatusOrRolesEvenForNoOp() {
        User self = user("ACTIVE", 3);
        when(userRepository.findByIdForUpdate(self.getId()))
                .thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.changeStatus(
                self.getId(), "ACTIVE", self.getId()
        )).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("chính mình");
        assertThatThrownBy(() -> service.updateRoles(
                self.getId(), Set.of("ROLE_ADMIN"), self.getId()
        )).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("chính mình");
    }

    @Test
    void validStatusChangeIncrementsAuthVersion() {
        User target = user("ACTIVE", 4);
        when(userRepository.findByIdForUpdate(target.getId()))
                .thenReturn(Optional.of(target));
        when(userRoleRepository.findAllByUserAndRevokedAtIsNull(target))
                .thenReturn(List.of());

        UUID actorId = UUID.randomUUID();
        service.changeStatus(
                target.getId(),
                "BANNED",
                actorId);

        assertThat(target.getStatus()).isEqualTo("BANNED");
        assertThat(target.getAuthVersion()).isEqualTo(5);
        verify(userRepository).saveAndFlush(target);
        ArgumentCaptor<AdminUserAudit> audit = ArgumentCaptor.forClass(
                AdminUserAudit.class);
        verify(auditRepository).saveAndFlush(audit.capture());
        assertThat(audit.getValue().getActorUserId()).isEqualTo(actorId);
        assertThat(audit.getValue().getTargetUserId()).isEqualTo(target.getId());
        assertThat(audit.getValue().getAction()).isEqualTo(
                AdminUserAuditAction.STATUS_CHANGED);
        assertThat(audit.getValue().getBeforeValue()).isEqualTo("ACTIVE");
        assertThat(audit.getValue().getAfterValue()).isEqualTo("BANNED");
    }

    @Test
    void roleChangeRejectsEmptyUnknownSelfRemovalAndLastAdmin() {
        UUID actorId = UUID.randomUUID();
        User target = user("ACTIVE", 0);

        assertThatThrownBy(() -> service.updateRoles(
                target.getId(), Set.of(), actorId)).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("ít nhất một");

        assertThatThrownBy(() -> service.updateRoles(
                target.getId(), Set.of("ROLE_UNKNOWN"), actorId)).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("không hợp lệ");

        Role admin = role(1L, "ROLE_ADMIN");
        Role citizen = role(2L, "ROLE_CITIZEN");
        User self = user("ACTIVE", 0);
        when(userRepository.findByIdForUpdate(self.getId()))
                .thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.updateRoles(
                self.getId(), Set.of("ROLE_CITIZEN"), self.getId())).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("chính mình");

        User lastAdmin = user("ACTIVE", 0);
        when(userRepository.findByIdForUpdate(lastAdmin.getId()))
                .thenReturn(Optional.of(lastAdmin));
        when(userRoleRepository.findAllByUser(lastAdmin)).thenReturn(List.of(
                assignment(lastAdmin, admin, null),
                assignment(lastAdmin, citizen, null)));
        when(userRoleRepository
                .findActiveAssignmentsByRoleNameAndUserStatusForUpdate(
                        "ROLE_ADMIN",
                        "ACTIVE"))
                .thenReturn(List.of(assignment(lastAdmin, admin, null)));

        assertThatThrownBy(() -> service.updateRoles(
                lastAdmin.getId(),
                Set.of("ROLE_CITIZEN"),
                actorId)).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("cuối cùng");
    }

    @Test
    void validRoleChangeRevokesRestoresAddsAndIncrementsVersion() {
        User target = user("ACTIVE", 7);
        Role citizen = role(2L, "ROLE_CITIZEN");
        Role rescuer = role(3L, "ROLE_RESCUER");
        Role coordinator = role(4L, "ROLE_COORDINATOR");

        UserRole activeCitizen = assignment(target, citizen, null);
        UserRole revokedRescuer = assignment(
                target,
                rescuer,
                LocalDateTime.now().minusDays(1));

        when(userRepository.findByIdForUpdate(target.getId()))
                .thenReturn(Optional.of(target));
        when(userRoleRepository.findAllByUser(target))
                .thenReturn(new java.util.ArrayList<>(List.of(
                        activeCitizen,
                        revokedRescuer)));
        when(roleRepository.findAllByRoleNameIn(any()))
                .thenReturn(List.of(rescuer, coordinator));

        service.updateRoles(
                target.getId(),
                Set.of("ROLE_RESCUER", "ROLE_COORDINATOR"),
                UUID.randomUUID());

        assertThat(activeCitizen.getRevokedAt()).isNotNull();
        assertThat(revokedRescuer.getRevokedAt()).isNull();
        assertThat(target.getAuthVersion()).isEqualTo(8);
        verify(userRoleRepository).saveAllAndFlush(any());
        verify(userRepository).saveAndFlush(target);
        ArgumentCaptor<AdminUserAudit> audit = ArgumentCaptor.forClass(
                AdminUserAudit.class);
        verify(auditRepository).saveAndFlush(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(
                AdminUserAuditAction.ROLES_CHANGED);
        assertThat(audit.getValue().getBeforeValue()).isEqualTo("ROLE_CITIZEN");
        assertThat(audit.getValue().getAfterValue()).isEqualTo(
                "ROLE_COORDINATOR,ROLE_RESCUER");
    }

    @Test
    void noOpStatusOrRoleChangeDoesNotIncrementVersion() {
        User target = user("ACTIVE", 3);
        Role citizen = role(2L, "ROLE_CITIZEN");

        when(userRepository.findByIdForUpdate(target.getId()))
                .thenReturn(Optional.of(target));
        service.changeStatus(
                target.getId(),
                "ACTIVE",
                UUID.randomUUID());

        when(userRoleRepository.findAllByUser(target))
                .thenReturn(List.of(assignment(target, citizen, null)));
        service.updateRoles(
                target.getId(),
                Set.of("ROLE_CITIZEN"),
                UUID.randomUUID());

        assertThat(target.getAuthVersion()).isEqualTo(3);
        verify(userRepository, never()).saveAndFlush(target);
        verify(userRoleRepository, never()).saveAllAndFlush(any());
        verifyNoInteractions(auditRepository);
    }

    private User user(String status, int authVersion) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayCode("USR-" + user.getId().toString().substring(0, 8));
        user.setFullName("Người dùng kiểm thử");
        user.setEmail(user.getId() + "@example.test");
        user.setPhoneNumber("0900000000");
        user.setPasswordHash("hash");
        user.setStatus(status);
        user.setAuthVersion(authVersion);
        return user;
    }

    private Role role(Long id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setRoleName(name);
        return role;
    }

    private UserRole assignment(
            User user,
            Role role,
            LocalDateTime revokedAt) {
        UserRole assignment = new UserRole();
        assignment.setId(new UserRoleId(user.getId(), role.getId()));
        assignment.setUser(user);
        assignment.setRole(role);
        assignment.setAssignedAt(LocalDateTime.now().minusDays(2));
        assignment.setRevokedAt(revokedAt);
        return assignment;
    }
}