package vn.edu.hcmute.drss.security;

import java.time.Instant;
import java.util.Objects;

public final class IssuedAccessToken {

    private final String tokenValue;
    private final Instant expiresAt;

    public IssuedAccessToken(
            String tokenValue,
            Instant expiresAt
    ) {
        this.tokenValue = Objects.requireNonNull(
                tokenValue,
                "JWT value is required"
        );
        this.expiresAt = Objects.requireNonNull(
                expiresAt,
                "JWT expiry is required"
        );
    }

    public String tokenValue() {
        return tokenValue;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "IssuedAccessToken[tokenValue=<redacted>, expiresAt="
                + expiresAt
                + "]";
    }
}