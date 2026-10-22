package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.CoordinatorAssignmentRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionAction;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestFilter;
import vn.edu.hcmute.drss.dtos.CoordinatorTrackingFilter;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;
import vn.edu.hcmute.drss.services.BusinessNotificationService;
import vn.edu.hcmute.drss.services.CoordinatorAssignmentService;
import vn.edu.hcmute.drss.services.CoordinatorRequestCommandService;
import vn.edu.hcmute.drss.services.CoordinatorRequestQueryService;
import vn.edu.hcmute.drss.services.CoordinatorTrackingService;
import vn.edu.hcmute.drss.services.RescuerService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("it")
@Transactional
class ConflictOfInterestIntegrationIT {

    private static final UUID COORDINATOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID OWNER_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID TEAM_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555"
    );
    private static final UUID VERIFIED_REQUEST_ID = UUID.fromString(
            "66666666-6666-6666-6666-666666666666"
    );
    private static final UUID ASSIGNED_REQUEST_ID = UUID.fromString(
            "88888888-8888-8888-8888-888888888888"
    );
    private static final UUID ASSIGNMENT_ID = UUID.fromString(
            "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    );

    @Autowired
    private RescueRequestRepository requests;
    @Autowired
    private RescueAssignmentRepository assignments;
    @Autowired
    private StatusHistoryRepository histories;
    @Autowired
    private CoordinatorRequestQueryService queryService;
    @Autowired
    private CoordinatorTrackingService trackingService;
    @Autowired
    private CoordinatorRequestCommandService commandService;
    @Autowired
    private CoordinatorAssignmentService assignmentService;
    @Autowired
    private RescuerService rescuerService;
    @Autowired
    private BusinessNotificationService notifications;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerIsExcludedFromCoordinatorReadAndCommandPaths() {
        RescueRequest request = requests.findById(VERIFIED_REQUEST_ID)
                .orElseThrow();
        request.setStatus("PENDING");
        requests.saveAndFlush(request);

        assertThat(queryService.findPending(
                OWNER_ID,
                new CoordinatorRequestFilter(),
                0
        ).getContent()).extracting(RescueRequest::getId)
                .doesNotContain(VERIFIED_REQUEST_ID);

        assertThat(trackingService.findTracking(
                OWNER_ID,
                new CoordinatorTrackingFilter(),
                0
        ).getContent()).allSatisfy(item ->
                assertThat(item.request().getUser().getId())
                        .isNotEqualTo(OWNER_ID)
        );

        assertThatThrownBy(() -> queryService.getDetail(
                VERIFIED_REQUEST_ID,
                OWNER_ID
        )).isInstanceOf(CoordinatorRequestNotFoundException.class);

        CoordinatorDecisionRequest command = new CoordinatorDecisionRequest();
        command.setAction(CoordinatorDecisionAction.VERIFY);
        command.setVersion(request.getVersion());

        assertThatThrownBy(() -> commandService.decide(
                VERIFIED_REQUEST_ID,
                OWNER_ID,
                command
        )).isInstanceOf(CoordinatorRequestNotFoundException.class);
    }

    @Test
    void ownerCannotUseRescuerPathOrReceiveOwnTeamAssignment() {
        addOwnerToTeam();

        assertThat(rescuerService.getAssignmentsForUser(OWNER_ID))
                .extracting(RescueAssignment::getId)
                .doesNotContain(ASSIGNMENT_ID);

        assertThatThrownBy(() -> rescuerService.getAssignmentDetail(
                ASSIGNMENT_ID,
                OWNER_ID
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(assignmentService.findAvailableTeams(OWNER_ID))
                .extracting(team -> team.getId())
                .doesNotContain(TEAM_ID);

        RescueRequest request = requests.findById(VERIFIED_REQUEST_ID)
                .orElseThrow();
        CoordinatorAssignmentRequest command =
                new CoordinatorAssignmentRequest();
        command.setTeamId(TEAM_ID);
        command.setVersion(request.getVersion());

        assertThatThrownBy(() -> assignmentService.assign(
                VERIFIED_REQUEST_ID,
                COORDINATOR_ID,
                command
        )).isInstanceOf(CoordinatorRequestConflictException.class)
                .hasMessageContaining("người gửi yêu cầu");
    }

    @Test
    void ownerIsExcludedFromCoordinatorAndTeamRecipients() {
        addOwnerCoordinatorRole();
        addOwnerToTeam();

        RescueRequest request = requests.findById(VERIFIED_REQUEST_ID)
                .orElseThrow();
        assertThat(notifications.notifyNewRequest(request))
                .extracting(row -> row.getRecipientUser().getId())
                .doesNotContain(OWNER_ID);

        RescueAssignment assignment = assignments.findById(ASSIGNMENT_ID)
                .orElseThrow();
        assertThat(notifications.notifyAssignmentCreated(assignment))
                .extracting(row -> row.getRecipientUser().getId())
                .doesNotContain(OWNER_ID);
    }

    @Test
    void actorRoleIsPersistedAndLegacyRowsAreBackfilled() {
        assertThat(histories
                .findByRequest_IdOrderByCreatedAtAscIdAsc(
                        VERIFIED_REQUEST_ID
                ))
                .extracting(history -> history.getActorRole())
                .contains("SYSTEM");

        RescueRequest request = requests.findById(VERIFIED_REQUEST_ID)
                .orElseThrow();
        request.setStatus("PENDING");
        requests.saveAndFlush(request);

        CoordinatorDecisionRequest command = new CoordinatorDecisionRequest();
        command.setAction(CoordinatorDecisionAction.VERIFY);
        command.setVersion(request.getVersion());
        commandService.decide(
                VERIFIED_REQUEST_ID,
                COORDINATOR_ID,
                command
        );

        assertThat(histories
                .findByRequest_IdOrderByCreatedAtAscIdAsc(
                        VERIFIED_REQUEST_ID
                )).last()
                .extracting(history -> history.getActorRole())
                .isEqualTo("ROLE_COORDINATOR");
    }

    private void addOwnerCoordinatorRole() {
        jdbcTemplate.update(
                """
                INSERT INTO user_roles (
                    user_id,
                    role_id,
                    assigned_at,
                    revoked_at
                ) VALUES (
                    UNHEX(REPLACE(?, '-', '')),
                    (SELECT id FROM roles
                     WHERE role_name = 'ROLE_COORDINATOR'),
                    CURRENT_TIMESTAMP(6),
                    NULL
                )
                ON DUPLICATE KEY UPDATE revoked_at = NULL
                """,
                OWNER_ID.toString()
        );
    }

    private void addOwnerToTeam() {
        jdbcTemplate.update(
                """
                INSERT INTO rescue_team_members (
                    team_id,
                    user_id,
                    role,
                    joined_at,
                    left_at
                ) VALUES (
                    UNHEX(REPLACE(?, '-', '')),
                    UNHEX(REPLACE(?, '-', '')),
                    'Member',
                    CURRENT_TIMESTAMP(6),
                    NULL
                )
                ON DUPLICATE KEY UPDATE left_at = NULL
                """,
                TEAM_ID.toString(),
                OWNER_ID.toString()
        );
    }
}