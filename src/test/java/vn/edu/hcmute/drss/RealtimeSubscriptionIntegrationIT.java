package vn.edu.hcmute.drss;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.hcmute.drss.services.RealtimeSubscriptionService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it")
class RealtimeSubscriptionIntegrationIT {

    private static final UUID RESCUER_ID = UUID.fromString(
            "44444444-4444-4444-4444-444444444444");
    private static final UUID TEAM_ID = UUID.fromString(
            "55555555-5555-5555-5555-555555555555");

    @Autowired
    private RealtimeSubscriptionService subscriptions;

    @Test
    void activeRescuerReceivesOwnTeamEvenOutsideTeamPage() {
        assertThat(subscriptions.eligibleTeamIds(RESCUER_ID, "ROLE_RESCUER"))
                .contains(TEAM_ID);
        assertThat(subscriptions.eligibleTeamIds(RESCUER_ID, "ROLE_CITIZEN"))
                .isEmpty();
    }
}
