package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;

public record PasswordResetRequestResult(
        Status status,
        LocalDateTime blockedUntil
) {

    public enum Status {
        ACCEPTED,
        RATE_LIMITED
    }

    public PasswordResetRequestResult {
        if (status == null) {
            throw new IllegalArgumentException("Status is required");
        }

        if (status == Status.ACCEPTED && blockedUntil != null) {
            throw new IllegalArgumentException(
                    "Accepted result must not contain blockedUntil"
            );
        }

        if (status == Status.RATE_LIMITED
                && blockedUntil == null) {
            throw new IllegalArgumentException(
                    "Rate-limited result requires blockedUntil"
            );
        }
    }

    public static PasswordResetRequestResult accepted() {
        return new PasswordResetRequestResult(
                Status.ACCEPTED,
                null
        );
    }

    public static PasswordResetRequestResult rateLimited(
            LocalDateTime blockedUntil
    ) {
        return new PasswordResetRequestResult(
                Status.RATE_LIMITED,
                blockedUntil
        );
    }
}