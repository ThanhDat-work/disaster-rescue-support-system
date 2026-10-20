package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;
import java.util.Objects;

public final class IssuedOtp {

    private final String code;
    private final LocalDateTime expiresAt;

    public IssuedOtp(String code, LocalDateTime expiresAt) {
        this.code = Objects.requireNonNull(code, "OTP code is required");
        this.expiresAt = Objects.requireNonNull(
                expiresAt,
                "OTP expiry is required"
        );
    }

    public String code() {
        return code;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    @Override
    public String toString() {
        return "IssuedOtp[code=<redacted>, expiresAt=" + expiresAt + "]";
    }
}