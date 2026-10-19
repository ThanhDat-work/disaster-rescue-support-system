package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.exceptions.OtpMailDeliveryException;
import vn.edu.hcmute.drss.repositories.UserRepository;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
public class OtpResendService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final UserRepository userRepository;
    private final VerificationTokenService tokenService;
    private final AuthRateLimitService rateLimitService;
    private final GmailOtpEmailService emailService;

    public OtpResendService(
            UserRepository userRepository,
            VerificationTokenService tokenService,
            AuthRateLimitService rateLimitService,
            GmailOtpEmailService emailService
    ) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.rateLimitService = rateLimitService;
        this.emailService = emailService;
    }

    public OtpResendResult resend(
            String email,
            VerificationTokenPurpose purpose,
            String ipAddress
    ) {
        String normalizedEmail = normalizeRequiredEmail(email);
        Objects.requireNonNull(purpose, "OTP purpose is required");

        RateLimitDecision decision =
                rateLimitService.consumePublicRequest(
                        AuthRateLimitAction.RESEND_OTP,
                        normalizedEmail,
                        ipAddress
                );

        if (!decision.allowed()) {
            return OtpResendResult.rateLimited(
                    decision.blockedUntil()
            );
        }

        Optional<User> userOptional =
                userRepository.findByEmailIgnoreCase(
                        normalizedEmail
                );

        if (userOptional.isEmpty()
                || !isEligible(userOptional.get(), purpose)) {
            return OtpResendResult.accepted();
        }

        User user = userOptional.get();
        IssuedOtp issuedOtp = tokenService.issue(user, purpose);

        try {
            emailService.sendOtp(
                    user.getEmail(),
                    purpose,
                    issuedOtp.code()
            );
        } catch (OtpMailDeliveryException exception) {
            // Phản hồi vẫn chung để không làm lộ tài khoản.
        }

        return OtpResendResult.accepted();
    }

    private boolean isEligible(
            User user,
            VerificationTokenPurpose purpose
    ) {
        return switch (purpose) {
            case ACCOUNT_ACTIVATION ->
                    STATUS_INACTIVE.equals(user.getStatus());
            case PASSWORD_RESET ->
                    STATUS_ACTIVE.equals(user.getStatus());
        };
    }

    private String normalizeRequiredEmail(String email) {
        String normalized = email == null
                ? ""
                : email.strip().toLowerCase(Locale.ROOT);

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        return normalized;
    }
}