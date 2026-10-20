package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;
import java.util.UUID;

public record PasswordResetVerificationResult(
        Status status,
        UUID verifiedTokenId,
        LocalDateTime blockedUntil
) {

    public enum Status {
        VERIFIED,
        INVALID,
        RATE_LIMITED
    }

    public PasswordResetVerificationResult {
        if (status == null) {
            throw new IllegalArgumentException("Status is required");
        }

        if (status == Status.VERIFIED
                && (verifiedTokenId == null
                || blockedUntil != null)) {
            throw new IllegalArgumentException(
                    "Verified result requires only a token id"
            );
        }

        if (status == Status.INVALID
                && (verifiedTokenId != null
                || blockedUntil != null)) {
            throw new IllegalArgumentException(
                    "Invalid result must not expose internal state"
            );
        }

        if (status == Status.RATE_LIMITED
                && (verifiedTokenId != null
                || blockedUntil == null)) {
            throw new IllegalArgumentException(
                    "Rate-limited result requires only blockedUntil"
            );
        }
    }

    public static PasswordResetVerificationResult verified(
            UUID verifiedTokenId
    ) {
        return new PasswordResetVerificationResult(
                Status.VERIFIED,
                verifiedTokenId,
                null
        );
    }

    public static PasswordResetVerificationResult invalid() {
        return new PasswordResetVerificationResult(
                Status.INVALID,
                null,
                null
        );
    }

    public static PasswordResetVerificationResult rateLimited(
            LocalDateTime blockedUntil
    ) {
        return new PasswordResetVerificationResult(
                Status.RATE_LIMITED,
                null,
                blockedUntil
        );
    }
}