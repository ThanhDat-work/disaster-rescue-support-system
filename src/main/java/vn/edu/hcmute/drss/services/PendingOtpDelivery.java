package vn.edu.hcmute.drss.services;

import java.util.Objects;

public final class PendingOtpDelivery {

    private final String recipient;
    private final String otp;

    public PendingOtpDelivery(
            String recipient,
            String otp
    ) {
        this.recipient = Objects.requireNonNull(
                recipient,
                "OTP recipient is required"
        );
        this.otp = Objects.requireNonNull(
                otp,
                "OTP code is required"
        );
    }

    public String recipient() {
        return recipient;
    }

    public String otp() {
        return otp;
    }

    @Override
    public String toString() {
        return "PendingOtpDelivery[recipient=<redacted>, otp=<redacted>]";
    }
}