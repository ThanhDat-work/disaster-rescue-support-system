package vn.edu.hcmute.drss;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it")
@Transactional
class NotificationRecipientIntegrationIT {

    private static final UUID COORDINATOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID RESCUER_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444"
    );
    private static final UUID TEAM_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555"
    );

    @Autowired
    private UserRoleRepository userRoles;
    @Autowired
    private RescueTeamMemberRepository teamMembers;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void revokedCoordinatorRoleIsExcludedFromNewRequestRecipients() {
        assertThat(userRoles.findActiveUsersByRoleName(
                "ROLE_COORDINATOR"
        )).extracting(user -> user.getId())
                .contains(COORDINATOR_ID);

        jdbcTemplate.update(
                """
                UPDATE user_roles
                SET revoked_at = CURRENT_TIMESTAMP(6)
                WHERE user_id = UNHEX(REPLACE(?, '-', ''))
                  AND role_id = (
                      SELECT id FROM roles
                      WHERE role_name = 'ROLE_COORDINATOR'
                  )
                """,
                COORDINATOR_ID.toString()
        );
        entityManager.clear();

        assertThat(userRoles.findActiveUsersByRoleName(
                "ROLE_COORDINATOR"
        )).extracting(user -> user.getId())
                .doesNotContain(COORDINATOR_ID);
    }

    @Test
    void nonActiveCoordinatorIsExcludedFromNewRequestRecipients() {
        jdbcTemplate.update(
                """
                UPDATE users
                SET status = 'INACTIVE'
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                COORDINATOR_ID.toString()
        );
        entityManager.clear();

        assertThat(userRoles.findActiveUsersByRoleName(
                "ROLE_COORDINATOR"
        )).extracting(user -> user.getId())
                .doesNotContain(COORDINATOR_ID);
    }

    @Test
    void leftTeamMemberIsExcludedFromAssignmentRecipients() {
        assertThat(teamMembers.findActiveUsersByTeamId(TEAM_ID))
                .extracting(user -> user.getId())
                .contains(RESCUER_ID);

        jdbcTemplate.update(
                """
                UPDATE rescue_team_members
                SET left_at = CURRENT_TIMESTAMP(6)
                WHERE team_id = UNHEX(REPLACE(?, '-', ''))
                  AND user_id = UNHEX(REPLACE(?, '-', ''))
                """,
                TEAM_ID.toString(),
                RESCUER_ID.toString()
        );
        entityManager.clear();

        assertThat(teamMembers.findActiveUsersByTeamId(TEAM_ID))
                .extracting(user -> user.getId())
                .doesNotContain(RESCUER_ID);
    }

    @Test
    void nonActiveTeamMemberIsExcludedFromAssignmentRecipients() {
        jdbcTemplate.update(
                """
                UPDATE users
                SET status = 'INACTIVE'
                WHERE id = UNHEX(REPLACE(?, '-', ''))
                """,
                RESCUER_ID.toString()
        );
        entityManager.clear();

        assertThat(teamMembers.findActiveUsersByTeamId(TEAM_ID))
                .extracting(user -> user.getId())
                .doesNotContain(RESCUER_ID);
    }
}