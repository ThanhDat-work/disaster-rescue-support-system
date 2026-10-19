package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;

public record ActivationResult(
        Status status,
        LocalDateTime retryAfter
) {

    public enum Status {
        ACTIVATED,
        INVALID,
        RATE_LIMITED
    }

    public ActivationResult {
        if (status == null) {
            throw new IllegalArgumentException(
                    "Activation status is required"
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

    public static ActivationResult activated() {
        return new ActivationResult(
                Status.ACTIVATED,
                null
        );
    }

    public static ActivationResult invalid() {
        return new ActivationResult(
                Status.INVALID,
                null
        );
    }

    public static ActivationResult rateLimited(
            LocalDateTime retryAfter
    ) {
        return new ActivationResult(
                Status.RATE_LIMITED,
                retryAfter
        );
    }
}