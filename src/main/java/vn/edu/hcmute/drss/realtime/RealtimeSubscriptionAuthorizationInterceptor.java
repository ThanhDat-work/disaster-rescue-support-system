package vn.edu.hcmute.drss.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;

import java.security.Principal;
import java.util.UUID;

@Component
public class RealtimeSubscriptionAuthorizationInterceptor
        implements ChannelInterceptor {

    static final String PERSONAL_DESTINATION =
            "/user/queue/notifications";
    static final String COORDINATOR_DESTINATION =
            "/user/queue/coordinator/notifications";
    static final String TEAM_PREFIX = "/user/queue/teams/";
    static final String TEAM_SUFFIX = "/notifications";

    private final UserRoleRepository userRoles;
    private final RescueTeamMemberRepository teamMembers;

    public RealtimeSubscriptionAuthorizationInterceptor(
            UserRoleRepository userRoles,
            RescueTeamMemberRepository teamMembers
    ) {
        this.userRoles = userRoles;
        this.teamMembers = teamMembers;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(message);

        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        AuthenticatedUserPrincipal principal = principal(accessor.getUser());
        String destination = accessor.getDestination();

        if (PERSONAL_DESTINATION.equals(destination)) {
            return message;
        }

        if (COORDINATOR_DESTINATION.equals(destination)) {
            if (userRoles.countActiveRoleForRealtime(
                    principal.userId(),
                    "ROLE_COORDINATOR"
            ) == 1L) {
                return message;
            }
            throw denied();
        }

        UUID teamId = parseTeamId(destination);
        if (teamId != null
                && teamMembers.countActiveMembershipForRealtime(
                        principal.userId(),
                        teamId
                ) == 1L) {
            return message;
        }

        throw denied();
    }

    private AuthenticatedUserPrincipal principal(Principal rawPrincipal) {
        if (!(rawPrincipal instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof AuthenticatedUserPrincipal principal)) {
            throw denied();
        }
        return principal;
    }

    private UUID parseTeamId(String destination) {
        if (destination == null
                || !destination.startsWith(TEAM_PREFIX)
                || !destination.endsWith(TEAM_SUFFIX)) {
            return null;
        }

        String value = destination.substring(
                TEAM_PREFIX.length(),
                destination.length() - TEAM_SUFFIX.length()
        );

        if (value.isBlank() || value.contains("/")) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException(
                "Không được phép subscribe destination này."
        );
    }
}