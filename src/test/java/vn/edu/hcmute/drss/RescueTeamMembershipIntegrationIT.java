package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.AdminRescueTeamRequest;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.services.AdminRescueTeamService;
import vn.edu.hcmute.drss.services.AdminUserService;
import vn.edu.hcmute.drss.services.RescuerService;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("it")
@Transactional
class RescueTeamMembershipIntegrationIT {

    private static final UUID ADMIN_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID RESCUER_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444"
    );

    @Autowired AdminRescueTeamService teams;
    @Autowired RescuerService rescuers;
    @Autowired AdminUserService users;

    @Test
    void adminCreatesTeamAndRescuerCanSeeOnlyMembershipSafeView() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var created = teams.create(new AdminRescueTeamRequest(
                "IT-" + suffix,
                "Integration Team " + suffix,
                "",
                "0912345678",
                "AVAILABLE",
                RESCUER_ID,
                Set.of()
        ));

        var detail = teams.getTeamView(created.getId());
        assertThat(detail.leader().userId()).isEqualTo(RESCUER_ID);
        assertThat(detail.members()).hasSize(1);
        assertThat(rescuers.getMyTeams(RESCUER_ID))
                .extracting(row -> row.id())
                .contains(created.getId());
        assertThat(rescuers.getMyTeam(created.getId(), RESCUER_ID)
                .members()).allMatch(row -> row.teamRole() != null);
    }

    @Test
    void rescuerRoleCannotBeRevokedWhileMembershipIsActive() {
        assertThatThrownBy(() -> users.updateRoles(
                RESCUER_ID,
                Set.of("ROLE_CITIZEN"),
                ADMIN_ID
        )).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("mọi đội");
    }
}