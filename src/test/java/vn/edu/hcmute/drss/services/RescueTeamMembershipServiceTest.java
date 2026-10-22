package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.dtos.AdminRescueTeamRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.entities.RescueTeamMember;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RescueTeamMembershipServiceTest {

    @Mock RescueTeamRepository teams;
    @Mock RescueTeamMemberRepository members;
    @Mock UserRepository users;

    private AdminRescueTeamService service;

    @BeforeEach
    void setUp() {
        service = new AdminRescueTeamService(teams, members, users);
    }

    @Test
    void createPersistsOneLeaderAndSelectedMembersAtomically() {
        User leader = user("Leader");
        User member = user("Member");
        when(users.findEligibleRescuersByIdInForShare(
                Set.of(leader.getId(), member.getId())
        )).thenReturn(List.of(leader, member));
        when(teams.saveAndFlush(any())).thenAnswer(invocation -> {
            RescueTeam team = invocation.getArgument(0);
            team.setId(UUID.randomUUID());
            return team;
        });
        when(members.findAllByTeam_Id(any())).thenReturn(List.of());

        RescueTeam created = service.create(new AdminRescueTeamRequest(
                "TEAM-X", "Đội X", "", "0900000000", "AVAILABLE",
                leader.getId(), Set.of(member.getId())
        ));

        assertThat(created.getLeaderUser()).isSameAs(leader);
        assertThat(created.getLeaderName()).isEqualTo("Leader");
        ArgumentCaptor<List<RescueTeamMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(members).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).extracting(RescueTeamMember::getRole)
                .containsExactlyInAnyOrder("LEADER", "MEMBER");
    }

    @Test
    void leaderCannotAlsoBeMember() {
        UUID sameUser = UUID.randomUUID();
        assertThatThrownBy(() -> service.create(new AdminRescueTeamRequest(
                "TEAM-X", "Đội X", "", "0900000000", "AVAILABLE",
                sameUser, Set.of(sameUser)
        ))).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("đồng thời");
        verify(users, never()).findEligibleRescuersByIdInForShare(any());
    }

    @Test
    void forgedOrIneligibleCandidateIsRejected() {
        UUID leaderId = UUID.randomUUID();
        when(users.findEligibleRescuersByIdInForShare(Set.of(leaderId)))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.create(new AdminRescueTeamRequest(
                "TEAM-X", "Đội X", "", "0900000000", "AVAILABLE",
                leaderId, Set.of()
        ))).isInstanceOf(AdminDomainException.class)
                .hasMessageContaining("ACTIVE/ROLE_RESCUER");
    }

    private User user(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setDisplayCode("USR-" + user.getId().toString().substring(0, 8));
        user.setFullName(name);
        user.setEmail(user.getId() + "@test.local");
        user.setStatus("ACTIVE");
        return user;
    }
}