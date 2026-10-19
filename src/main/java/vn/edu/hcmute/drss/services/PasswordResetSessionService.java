package vn.edu.hcmute.drss.services;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetSessionService {

    private static final String EMAIL_ATTRIBUTE =
            PasswordResetSessionService.class.getName()
                    + ".normalizedEmail";

    private static final String TOKEN_ATTRIBUTE =
            PasswordResetSessionService.class.getName()
                    + ".verifiedTokenId";

    public void begin(
            HttpServletRequest request,
            String email
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "HTTP request is required"
            );
        }

        String normalizedEmail = normalizeEmail(email);

        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Email is required"
            );
        }

        HttpSession session = request.getSession(true);

        session.removeAttribute(TOKEN_ATTRIBUTE);
        session.setAttribute(
                EMAIL_ATTRIBUTE,
                normalizedEmail
        );
    }

    public Optional<String> pendingEmail(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }

        Object value = session.getAttribute(EMAIL_ATTRIBUTE);

        if (value instanceof String email && !email.isBlank()) {
            return Optional.of(email);
        }

        session.removeAttribute(EMAIL_ATTRIBUTE);
        return Optional.empty();
    }

    public void establishGrant(
            HttpServletRequest request,
            UUID verifiedTokenId
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "HTTP request is required"
            );
        }

        if (verifiedTokenId == null) {
            throw new IllegalArgumentException(
                    "Verified token id is required"
            );
        }

        HttpSession session = request.getSession(false);

        if (session == null || pendingEmail(session).isEmpty()) {
            throw new IllegalStateException(
                    "Password-reset flow has not been started"
            );
        }

        request.changeSessionId();

        session.removeAttribute(EMAIL_ATTRIBUTE);
        session.setAttribute(
                TOKEN_ATTRIBUTE,
                verifiedTokenId.toString()
        );
    }

    public Optional<UUID> verifiedTokenId(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }

        Object value = session.getAttribute(TOKEN_ATTRIBUTE);

        if (!(value instanceof String rawTokenId)
                || rawTokenId.isBlank()) {
            session.removeAttribute(TOKEN_ATTRIBUTE);
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(rawTokenId));
        } catch (IllegalArgumentException exception) {
            session.removeAttribute(TOKEN_ATTRIBUTE);
            return Optional.empty();
        }
    }

    public void clear(HttpSession session) {
        if (session == null) {
            return;
        }

        session.removeAttribute(EMAIL_ATTRIBUTE);
        session.removeAttribute(TOKEN_ATTRIBUTE);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }

        return email.strip().toLowerCase(Locale.ROOT);
    }
}