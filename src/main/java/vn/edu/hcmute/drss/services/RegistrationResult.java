package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;

public record RegistrationResult(
        Status status,
        LocalDateTime retryAfter
) {

    public enum Status {
        ACCEPTED,
        EMAIL_DELIVERY_FAILED,
        RATE_LIMITED
    }

    public RegistrationResult {
        if (status == null) {
            throw new IllegalArgumentException(
                    "Registration status is required"
            );
        }

        if (status == Status.RATE_LIMITED
                && retryAfter == null) {
            throw new IllegalArgumentException(
                    "Rate-limited result requires retryAfter"
            );
        }

        if (status != Status.RATE_LIMITED
                && retryAfter != null) {
            throw new IllegalArgumentException(
                    "Only rate-limited result may contain retryAfter"
            );
        }
    }

    public static RegistrationResult accepted() {
        return new RegistrationResult(
                Status.ACCEPTED,
                null
        );
    }

    public static RegistrationResult emailDeliveryFailed() {
        return new RegistrationResult(
                Status.EMAIL_DELIVERY_FAILED,
                null
        );
    }

    public static RegistrationResult rateLimited(
            LocalDateTime retryAfter
    ) {
        return new RegistrationResult(
                Status.RATE_LIMITED,
                retryAfter
        );
    }
}