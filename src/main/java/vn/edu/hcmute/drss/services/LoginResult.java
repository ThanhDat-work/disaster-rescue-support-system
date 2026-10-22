package vn.edu.hcmute.drss.services;

import vn.edu.hcmute.drss.security.IssuedAccessToken;

import java.time.LocalDateTime;
import java.util.UUID;

public final class LoginResult {

    public enum Status {
        AUTHENTICATED,
        ROLE_SELECTION_REQUIRED,
        INVALID_CREDENTIALS,
        INACTIVE,
        BANNED,
        NO_ACTIVE_ROLES,
        RATE_LIMITED
    }

    private final Status status;
    private final IssuedAccessToken accessToken;
    private final String activeRole;
    private final UUID pendingUserId;
    private final LocalDateTime retryAfter;

    private LoginResult(
            Status status,
            IssuedAccessToken accessToken,
            String activeRole,
            UUID pendingUserId,
            LocalDateTime retryAfter
    ) {
        this.status = status;
        this.accessToken = accessToken;
        this.activeRole = activeRole;
        this.pendingUserId = pendingUserId;
        this.retryAfter = retryAfter;
    }

    public static LoginResult authenticated(
            IssuedAccessToken accessToken,
            String activeRole
    ) {
        if (accessToken == null
                || activeRole == null
                || activeRole.isBlank()) {
            throw new IllegalArgumentException(
                    "Authenticated result requires token and active role"
            );
        }

        return new LoginResult(
                Status.AUTHENTICATED,
                accessToken,
                activeRole.strip(),
                null,
                null
        );
    }

    public static LoginResult roleSelectionRequired(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "Role selection requires user id"
            );
        }

        return new LoginResult(
                Status.ROLE_SELECTION_REQUIRED,
                null,
                null,
                userId,
                null
        );
    }

    public static LoginResult invalidCredentials() {
        return simple(Status.INVALID_CREDENTIALS);
    }

    public static LoginResult inactive() {
        return simple(Status.INACTIVE);
    }

    public static LoginResult banned() {
        return simple(Status.BANNED);
    }

    public static LoginResult noActiveRoles() {
        return simple(Status.NO_ACTIVE_ROLES);
    }

    public static LoginResult rateLimited(LocalDateTime retryAfter) {
        if (retryAfter == null) {
            throw new IllegalArgumentException(
                    "Rate-limited result requires retryAfter"
            );
        }

        return new LoginResult(
                Status.RATE_LIMITED,
                null,
                null,
                null,
                retryAfter
        );
    }

    private static LoginResult simple(Status status) {
        return new LoginResult(
                status,
                null,
                null,
                null,
                null
        );
    }

    public Status status() {
        return status;
    }

    public IssuedAccessToken accessToken() {
        return accessToken;
    }

    public String activeRole() {
        return activeRole;
    }

    public UUID pendingUserId() {
        return pendingUserId;
    }

    public LocalDateTime retryAfter() {
        return retryAfter;
    }

    @Override
    public String toString() {
        return "LoginResult[status="
                + status
                + ", accessToken="
                + (accessToken == null ? "none" : "<redacted>")
                + ", activeRole="
                + activeRole
                + ", pendingUserId="
                + pendingUserId
                + ", retryAfter="
                + retryAfter
                + "]";
    }
}