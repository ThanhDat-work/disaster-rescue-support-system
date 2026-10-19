package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;

public record OtpResendResult(
        Status status,
        LocalDateTime blockedUntil
) {
    public enum Status {
        ACCEPTED,
        RATE_LIMITED
    }

    public OtpResendResult {
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

    public static OtpResendResult accepted() {
        return new OtpResendResult(Status.ACCEPTED, null);
    }

    public static OtpResendResult rateLimited(
            LocalDateTime blockedUntil
    ) {
        return new OtpResendResult(
                Status.RATE_LIMITED,
                blockedUntil
        );
    }
}