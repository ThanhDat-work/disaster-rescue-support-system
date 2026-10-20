package vn.edu.hcmute.drss.services;

import vn.edu.hcmute.drss.security.IssuedAccessToken;

import java.time.LocalDateTime;

public final class LoginResult {

    public enum Status {
        AUTHENTICATED,
        INVALID_CREDENTIALS,
        INACTIVE,
        BANNED,
        NO_ACTIVE_ROLES,
        RATE_LIMITED
    }

    private final Status status;
    private final IssuedAccessToken accessToken;
    private final LocalDateTime retryAfter;

    private LoginResult(
            Status status,
            IssuedAccessToken accessToken,
            LocalDateTime retryAfter
    ) {
        this.status = status;
        this.accessToken = accessToken;
        this.retryAfter = retryAfter;
    }

    public static LoginResult authenticated(
            IssuedAccessToken accessToken
    ) {
        if (accessToken == null) {
            throw new IllegalArgumentException(
                    "Authenticated result requires an access token"
            );
        }

        return new LoginResult(
                Status.AUTHENTICATED,
                accessToken,
                null
        );
    }

    public static LoginResult invalidCredentials() {
        return new LoginResult(
                Status.INVALID_CREDENTIALS,
                null,
                null
        );
    }

    public static LoginResult inactive() {
        return new LoginResult(
                Status.INACTIVE,
                null,
                null
        );
    }

    public static LoginResult banned() {
        return new LoginResult(
                Status.BANNED,
                null,
                null
        );
    }

    public static LoginResult noActiveRoles() {
        return new LoginResult(
                Status.NO_ACTIVE_ROLES,
                null,
                null
        );
    }

    public static LoginResult rateLimited(
            LocalDateTime retryAfter
    ) {
        if (retryAfter == null) {
            throw new IllegalArgumentException(
                    "Rate-limited result requires retryAfter"
            );
        }

        return new LoginResult(
                Status.RATE_LIMITED,
                null,
                retryAfter
        );
    }

    public Status status() {
        return status;
    }

    public IssuedAccessToken accessToken() {
        return accessToken;
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
                + ", retryAfter="
                + retryAfter
                + "]";
    }
}