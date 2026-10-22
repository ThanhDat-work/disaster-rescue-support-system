package vn.edu.hcmute.drss.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeSubscriptionAuthorizationInterceptorTest {

    @Mock
    private UserRoleRepository userRoles;
    @Mock
    private RescueTeamMemberRepository teamMembers;
    @Mock
    private MessageChannel channel;

    private RealtimeSubscriptionAuthorizationInterceptor interceptor;
    private UUID userId;

    @BeforeEach
    void setUp() {
        interceptor = new RealtimeSubscriptionAuthorizationInterceptor(
                userRoles,
                teamMembers
        );
        userId = UUID.randomUUID();
    }

    @Test
    void personalDestinationAllowsAuthenticatedUser() {
        Message<byte[]> message = subscribe(
                "/user/queue/notifications",
                "ROLE_CITIZEN"
        );

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void coordinatorDestinationRechecksCurrentDatabaseRole() {
        when(userRoles.countActiveRoleForRealtime(
                userId,
                "ROLE_COORDINATOR"
        )).thenReturn(1L, 0L);

        Message<byte[]> allowed = subscribe(
                "/user/queue/coordinator/notifications",
                "ROLE_COORDINATOR"
        );
        assertThat(interceptor.preSend(allowed, channel)).isSameAs(allowed);

        Message<byte[]> revoked = subscribe(
                "/user/queue/coordinator/notifications",
                "ROLE_COORDINATOR"
        );
        assertThatThrownBy(() -> interceptor.preSend(revoked, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void forgedTeamDestinationIsDenied() {
        UUID activeTeamId = UUID.randomUUID();
        UUID forgedTeamId = UUID.randomUUID();
        when(teamMembers.countActiveMembershipForRealtime(
                userId,
                activeTeamId
        )).thenReturn(1L);

        Message<byte[]> allowed = subscribe(
                "/user/queue/teams/" + activeTeamId + "/notifications",
                "ROLE_RESCUER"
        );
        assertThat(interceptor.preSend(allowed, channel)).isSameAs(allowed);

        Message<byte[]> forged = subscribe(
                "/user/queue/teams/" + forgedTeamId + "/notifications",
                "ROLE_RESCUER"
        );
        assertThatThrownBy(() -> interceptor.preSend(forged, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unknownDestinationIsDenied() {
        Message<byte[]> message = subscribe(
                "/topic/all-notifications",
                "ROLE_ADMIN"
        );

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Message<byte[]> subscribe(
            String destination,
            String role
    ) {
        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(
                        userId,
                        "user@example.com",
                        "Realtime User",
                        0,
                        List.of(role)
                );
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                );

        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(authentication);

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}