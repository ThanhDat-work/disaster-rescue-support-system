package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.VerificationToken;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.VerificationTokenRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AccountActivationService {

    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final Pattern OTP_PATTERN =
            Pattern.compile("[0-9]{6}");

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final VerificationTokenService tokenService;
    private final AuthRateLimitService rateLimitService;
    private final Clock clock;

    public AccountActivationService(
            UserRepository userRepository,
            VerificationTokenRepository tokenRepository,
            VerificationTokenService tokenService,
            AuthRateLimitService rateLimitService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.rateLimitService = rateLimitService;
        this.clock = clock;
    }

    @Transactional
    public ActivationResult activate(
            String email,
            String candidateOtp,
            String ipAddress
    ) {
        RateLimitDecision initialDecision =
                rateLimitService.checkVerificationAllowed(
                        AuthRateLimitAction.VERIFY_ACTIVATION,
                        ipAddress
                );

        if (!initialDecision.allowed()) {
            return ActivationResult.rateLimited(
                    initialDecision.blockedUntil()
            );
        }

        String normalizedEmail = normalizeEmail(email);

        Optional<User> userOptional =
                userRepository.findByEmailIgnoreCase(
                        normalizedEmail
                );

        if (userOptional.isEmpty()
                || !STATUS_INACTIVE.equals(
                        userOptional.get().getStatus()
                )
                || !isOtpFormatValid(candidateOtp)) {
            return recordGenericFailure(ipAddress);
        }

        User user = userOptional.get();

        Optional<UUID> verifiedTokenId =
                tokenService.verify(
                        user,
                        VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                        candidateOtp
                );

        if (verifiedTokenId.isEmpty()) {
            return recordGenericFailure(ipAddress);
        }

        Optional<VerificationToken> tokenOptional =
                tokenRepository.findById(
                        verifiedTokenId.get()
                );

        if (tokenOptional.isEmpty()
                || !isVerifiedActivationToken(
                        tokenOptional.get(),
                        user
                )) {
            return recordGenericFailure(ipAddress);
        }

        VerificationToken token = tokenOptional.get();
        LocalDateTime now = currentDateTime();

        user.setStatus(STATUS_ACTIVE);
        token.setConsumedAt(now);

        userRepository.saveAndFlush(user);
        tokenRepository.saveAndFlush(token);

        return ActivationResult.activated();
    }

    private ActivationResult recordGenericFailure(
            String ipAddress
    ) {
        RateLimitDecision failureDecision =
                rateLimitService.recordVerificationFailure(
                        AuthRateLimitAction.VERIFY_ACTIVATION,
                        ipAddress
                );

        if (!failureDecision.allowed()) {
            return ActivationResult.rateLimited(
                    failureDecision.blockedUntil()
            );
        }

        return ActivationResult.invalid();
    }

    private boolean isVerifiedActivationToken(
            VerificationToken token,
            User user
    ) {
        return token.getPurpose()
                == VerificationTokenPurpose.ACCOUNT_ACTIVATION
                && token.getVerifiedAt() != null
                && token.getConsumedAt() == null
                && token.getUser() != null
                && Objects.equals(
                        token.getUser().getId(),
                        user.getId()
                );
    }

    private boolean isOtpFormatValid(String candidateOtp) {
        return candidateOtp != null
                && OTP_PATTERN.matcher(candidateOtp).matches();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }

        return email.strip().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneId.systemDefault()
        );
    }
}