package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;

public record RateLimitDecision(
        boolean allowed,
        LocalDateTime blockedUntil
) {

    public RateLimitDecision {
        if (allowed && blockedUntil != null) {
            throw new IllegalArgumentException(
                    "An allowed decision must not contain blockedUntil"
            );
        }

        if (!allowed && blockedUntil == null) {
            throw new IllegalArgumentException(
                    "A blocked decision must contain blockedUntil"
            );
        }
    }

    public static RateLimitDecision allowedDecision() {
        return new RateLimitDecision(true, null);
    }

    public static RateLimitDecision blockedUntil(
            LocalDateTime blockedUntil
    ) {
        return new RateLimitDecision(false, blockedUntil);
    }
}