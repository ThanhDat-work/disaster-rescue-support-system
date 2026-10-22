package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.Role;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.entities.UserRoleId;
import vn.edu.hcmute.drss.repositories.RoleRepository;
import vn.edu.hcmute.drss.repositories.RoleSwitchAuditRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.security.JwtAuthenticationService;
import vn.edu.hcmute.drss.services.ActiveRoleChangeResult;
import vn.edu.hcmute.drss.services.RoleSwitchService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it")
@Transactional
class ActiveRoleIntegrationIT {

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private UserRoleRepository userRoles;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private RoleSwitchService roleSwitchService;

    @Autowired
    private JwtAuthenticationService authenticationService;

    @Autowired
    private RoleSwitchAuditRepository audits;

    @Test
    void switchPersistsAuditAndJwtHasOnlyTargetAuthority() {
        Role coordinator = roles.findByRoleName("ROLE_COORDINATOR")
                .orElseThrow();
        Role rescuer = roles.findByRoleName("ROLE_RESCUER")
                .orElseThrow();

        User user = new User();
        user.setDisplayCode(
                "USR-T309-" + UUID.randomUUID().toString()
                        .substring(0, 6)
        );
        user.setFullName("T3-09 Multi Role");
        user.setEmail("t309-" + UUID.randomUUID() + "@example.test");
        user.setPhoneNumber("0901234567");
        user.setPasswordHash(passwords.encode("Correct pass 123"));
        user.setStatus("ACTIVE");
        user.setAuthVersion(0);
        user = users.saveAndFlush(user);

        grant(user, coordinator);
        grant(user, rescuer);

        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getAuthVersion(),
                        "ROLE_COORDINATOR"
                );

        ActiveRoleChangeResult result = roleSwitchService.switchRole(
                principal,
                "ROLE_RESCUER",
                "Correct pass 123",
                "203.0.113.90"
        );

        assertThat(result.status())
                .isEqualTo(ActiveRoleChangeResult.Status.SUCCESS);
        assertThat(authenticationService.authenticate(
                result.accessToken().tokenValue()
        )).get().satisfies(authentication ->
                assertThat(authentication.getAuthorities())
                        .extracting(authority -> authority.getAuthority())
                        .containsExactly("ROLE_RESCUER")
        );
        assertThat(audits.countByUser_Id(user.getId())).isEqualTo(1);
    }

    private void grant(User user, Role role) {
        UserRole assignment = new UserRole();
        assignment.setId(new UserRoleId(user.getId(), role.getId()));
        assignment.setUser(user);
        assignment.setRole(role);
        assignment.setAssignedAt(LocalDateTime.now());
        userRoles.saveAndFlush(assignment);
    }
}