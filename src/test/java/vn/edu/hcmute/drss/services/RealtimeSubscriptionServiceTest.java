package vn.edu.hcmute.drss.services;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.entities.RescueTeamMember;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeSubscriptionServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");
    private static final UUID TEAM_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");

    @Mock
    private RescueTeamMemberRepository members;

    @InjectMocks
    private RealtimeSubscriptionService service;

    @Test
    void nonRescuerNeverReceivesTeamDestinations() {
        assertThat(service.eligibleTeamIds(USER_ID, "ROLE_CITIZEN"))
                .isEmpty();
        assertThat(service.eligibleTeamIds(USER_ID, "ROLE_COORDINATOR"))
                .isEmpty();
        verifyNoInteractions(members);
    }

    @Test
    void rescuerGetsOnlyOwnEligibleTeamIdsWithoutDuplicates() {
        RescueTeam team = mock(RescueTeam.class);
        RescueTeamMember first = mock(RescueTeamMember.class);
        RescueTeamMember duplicate = mock(RescueTeamMember.class);
        when(team.getId()).thenReturn(TEAM_ID);
        when(first.getTeam()).thenReturn(team);
        when(duplicate.getTeam()).thenReturn(team);
        when(members.findEligibleMembershipsByUserId(USER_ID))
                .thenReturn(List.of(first, duplicate));

        assertThat(service.eligibleTeamIds(USER_ID, "ROLE_RESCUER"))
                .containsExactly(TEAM_ID);
    }
}
