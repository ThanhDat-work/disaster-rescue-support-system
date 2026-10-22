package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveRoleServiceTest {

    @Test
    void rolesUseLockedDisplayOrder() {
        assertThat(ActiveRoleService.sortRoles(List.of(
                "ROLE_ADMIN",
                "ROLE_RESCUER",
                "ROLE_CITIZEN",
                "ROLE_COORDINATOR",
                "ROLE_CITIZEN"
        ))).containsExactly(
                "ROLE_CITIZEN",
                "ROLE_COORDINATOR",
                "ROLE_RESCUER",
                "ROLE_ADMIN"
        );
    }

    @Test
    void eachRoleUsesRealApplicationLandingPath() {
        assertThat(ActiveRoleService.landingPath("ROLE_CITIZEN"))
                .isEqualTo("/requests");
        assertThat(ActiveRoleService.landingPath("ROLE_COORDINATOR"))
                .isEqualTo("/coordinator/pending");
        assertThat(ActiveRoleService.landingPath("ROLE_RESCUER"))
                .isEqualTo("/team/tasks");
        assertThat(ActiveRoleService.landingPath("ROLE_ADMIN"))
                .isEqualTo("/admin/users");
    }
}