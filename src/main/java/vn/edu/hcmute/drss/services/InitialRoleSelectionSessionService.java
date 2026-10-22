package vn.edu.hcmute.drss.services;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class InitialRoleSelectionSessionService {

    private static final String USER_ID_ATTRIBUTE =
            "T3_09_PENDING_ROLE_USER_ID";
    private static final String EXPIRES_AT_ATTRIBUTE =
            "T3_09_PENDING_ROLE_EXPIRES_AT";
    private static final Duration GRANT_TTL =
            Duration.ofMinutes(5);

    private final Clock clock;

    public InitialRoleSelectionSessionService(Clock clock) {
        this.clock = clock;
    }

    public void begin(
            HttpServletRequest request,
            UUID userId
    ) {
        if (request == null || userId == null) {
            throw new IllegalArgumentException(
                    "Request and user id are required"
            );
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(
                USER_ID_ATTRIBUTE,
                userId.toString()
        );
        session.setAttribute(
                EXPIRES_AT_ATTRIBUTE,
                clock.instant().plus(GRANT_TTL).toString()
        );
    }

    public Optional<UUID> resolve(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }

        Object rawUserId = session.getAttribute(USER_ID_ATTRIBUTE);
        Object rawExpiry = session.getAttribute(EXPIRES_AT_ATTRIBUTE);

        if (!(rawUserId instanceof String userIdValue)
                || !(rawExpiry instanceof String expiryValue)) {
            clear(session);
            return Optional.empty();
        }

        try {
            UUID userId = UUID.fromString(userIdValue);
            Instant expiresAt = Instant.parse(expiryValue);

            if (!clock.instant().isBefore(expiresAt)) {
                clear(session);
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (IllegalArgumentException exception) {
            clear(session);
            return Optional.empty();
        }
    }

    public void clear(HttpSession session) {
        if (session == null) {
            return;
        }
        session.removeAttribute(USER_ID_ATTRIBUTE);
        session.removeAttribute(EXPIRES_AT_ATTRIBUTE);
    }
}