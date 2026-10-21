package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.Role;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.entities.UserRoleId;
import vn.edu.hcmute.drss.repositories.RoleRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtAuthenticationService;
import vn.edu.hcmute.drss.security.JwtTokenService;
import vn.edu.hcmute.drss.services.AdminUserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AdminUserIntegrationIT {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtAuthenticationService jwtAuthenticationService;

    @Test
    void searchRoleMutationAndStatusMutationFollowContract() {
        User actor = userRepository
                .findByEmailIgnoreCase("admin@demo.com")
                .orElseThrow();

        Role citizen = roleRepository
                .findByRoleName("ROLE_CITIZEN")
                .orElseThrow();
        Role rescuer = roleRepository
                .findByRoleName("ROLE_RESCUER")
                .orElseThrow();

        User target = new User();
        target.setDisplayCode("USR-T301-" + UUID.randomUUID()
                .toString().substring(0, 6));
        target.setFullName("T3-01 Integration User");
        target.setEmail(
                "t301-" + UUID.randomUUID() + "@example.test");
        target.setPhoneNumber("0901234567");
        target.setPasswordHash("integration-test-hash");
        target.setStatus("ACTIVE");
        target.setAuthVersion(0);
        target = userRepository.saveAndFlush(target);

        UserRole citizenAssignment = new UserRole();
        citizenAssignment.setId(new UserRoleId(
                target.getId(),
                citizen.getId()));
        citizenAssignment.setUser(target);
        citizenAssignment.setRole(citizen);
        citizenAssignment.setAssignedAt(LocalDateTime.now());
        userRoleRepository.saveAndFlush(citizenAssignment);

        IssuedAccessToken oldToken = jwtTokenService.issue(
                target,
                List.of("ROLE_CITIZEN"));

        assertThat(jwtAuthenticationService.authenticate(
                oldToken.tokenValue())).isPresent();

        var searchResult = adminUserService.search(
                target.getEmail(),
                "ACTIVE",
                "ROLE_CITIZEN",
                0,
                20);

        assertThat(searchResult.getTotalElements()).isEqualTo(1);
        assertThat(searchResult.getContent().get(0).roles())
                .containsExactly("ROLE_CITIZEN");

        adminUserService.updateRoles(
                target.getId(),
                Set.of("ROLE_CITIZEN", "ROLE_RESCUER"),
                actor.getId());

        userRepository.flush();
        userRoleRepository.flush();

        User afterRoleUpdate = userRepository
                .findById(target.getId())
                .orElseThrow();

        assertThat(afterRoleUpdate.getAuthVersion()).isEqualTo(1);
        assertThat(userRoleRepository
                .findAllByUserAndRevokedAtIsNull(afterRoleUpdate))
                .extracting(assignment -> assignment.getRole().getRoleName())
                .containsExactlyInAnyOrder(
                        "ROLE_CITIZEN",
                        "ROLE_RESCUER");
        assertThat(jwtAuthenticationService.authenticate(
                oldToken.tokenValue())).isEmpty();

        IssuedAccessToken tokenAfterRoleUpdate = jwtTokenService.issue(
                afterRoleUpdate,
                List.of("ROLE_CITIZEN", "ROLE_RESCUER"));

        assertThat(jwtAuthenticationService.authenticate(
                tokenAfterRoleUpdate.tokenValue())).isPresent();

        adminUserService.changeStatus(
                target.getId(),
                "BANNED",
                actor.getId());

        User bannedUser = userRepository
                .findById(target.getId())
                .orElseThrow();

        assertThat(bannedUser.getStatus()).isEqualTo("BANNED");
        assertThat(bannedUser.getAuthVersion()).isEqualTo(2);
        assertThat(jwtAuthenticationService.authenticate(
                tokenAfterRoleUpdate.tokenValue())).isEmpty();
    }

    @Test
    void paginationUsesActualTotalPages() {
        User actor = userRepository
                .findByEmailIgnoreCase("admin@demo.com")
                .orElseThrow();

        assertThat(actor.getId()).isNotNull();

        var page = adminUserService.search(
                null,
                null,
                null,
                0,
                10);

        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(4);
    }
}