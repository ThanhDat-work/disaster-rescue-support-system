package vn.edu.hcmute.drss.services;

import vn.edu.hcmute.drss.security.IssuedAccessToken;

import java.time.LocalDateTime;

public record ActiveRoleChangeResult(
        Status status,
        IssuedAccessToken accessToken,
        String activeRole,
        String redirectPath,
        LocalDateTime retryAfter
) {
    public enum Status {
        SUCCESS,
        INVALID,
        RATE_LIMITED
    }

    public static ActiveRoleChangeResult success(
            IssuedAccessToken token,
            String activeRole,
            String redirectPath
    ) {
        return new ActiveRoleChangeResult(
                Status.SUCCESS,
                token,
                activeRole,
                redirectPath,
                null
        );
    }

    public static ActiveRoleChangeResult invalid() {
        return new ActiveRoleChangeResult(
                Status.INVALID,
                null,
                null,
                null,
                null
        );
    }

    public static ActiveRoleChangeResult rateLimited(
            LocalDateTime retryAfter
    ) {
        return new ActiveRoleChangeResult(
                Status.RATE_LIMITED,
                null,
                null,
                null,
                retryAfter
        );
    }

    @Override
    public String toString() {
        return "ActiveRoleChangeResult[status="
                + status
                + ", accessToken="
                + (accessToken == null ? "none" : "<redacted>")
                + ", activeRole="
                + activeRole
                + ", redirectPath="
                + redirectPath
                + ", retryAfter="
                + retryAfter
                + "]";
    }
}