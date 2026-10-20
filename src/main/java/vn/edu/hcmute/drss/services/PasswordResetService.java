package vn.edu.hcmute.drss.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.VerificationToken;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.exceptions.OtpMailDeliveryException;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.VerificationTokenRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PasswordResetService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final Duration RESET_GRANT_TTL =
            Duration.ofMinutes(10);
    private static final Pattern OTP_PATTERN =
            Pattern.compile("[0-9]{6}");

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final VerificationTokenService tokenService;
    private final AuthRateLimitService rateLimitService;
    private final GmailOtpEmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            VerificationTokenRepository tokenRepository,
            VerificationTokenService tokenService,
            AuthRateLimitService rateLimitService,
            GmailOtpEmailService emailService,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.rateLimitService = rateLimitService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public PasswordResetRequestResult requestReset(
            String email,
            String ipAddress
    ) {
        String normalizedEmail = normalizeRequiredEmail(email);

        RateLimitDecision decision =
                rateLimitService.consumePublicRequest(
                        AuthRateLimitAction.FORGOT_PASSWORD,
                        normalizedEmail,
                        ipAddress
                );

        if (!decision.allowed()) {
            return PasswordResetRequestResult.rateLimited(
                    decision.blockedUntil()
            );
        }

        Optional<User> userOptional =
                userRepository.findByEmailIgnoreCase(
                        normalizedEmail
                );

        if (userOptional.isEmpty()
                || !STATUS_ACTIVE.equals(
                        userOptional.get().getStatus()
                )) {
            return PasswordResetRequestResult.accepted();
        }

        User user = userOptional.get();

        IssuedOtp issuedOtp = tokenService.issue(
                user,
                VerificationTokenPurpose.PASSWORD_RESET
        );

        try {
            emailService.sendOtp(
                    user.getEmail(),
                    VerificationTokenPurpose.PASSWORD_RESET,
                    issuedOtp.code()
            );
        } catch (OtpMailDeliveryException exception) {
            /*
             * Token đã được commit trước khi Gmail được gọi.
             * Kết quả bên ngoài vẫn giống email không tồn tại để
             * không tạo account-enumeration oracle.
             */
        }

        return PasswordResetRequestResult.accepted();
    }

    @Transactional
    public PasswordResetVerificationResult verifyResetOtp(
            String email,
            String candidateOtp,
            String ipAddress
    ) {
        RateLimitDecision initialDecision =
                rateLimitService.checkVerificationAllowed(
                        AuthRateLimitAction.VERIFY_RESET,
                        ipAddress
                );

        if (!initialDecision.allowed()) {
            return PasswordResetVerificationResult.rateLimited(
                    initialDecision.blockedUntil()
            );
        }

        String normalizedEmail = normalizeEmail(email);

        Optional<User> userOptional = normalizedEmail.isBlank()
                ? Optional.empty()
                : userRepository.findByEmailIgnoreCase(
                        normalizedEmail
                );

        if (userOptional.isEmpty()
                || !STATUS_ACTIVE.equals(
                        userOptional.get().getStatus()
                )
                || !isOtpFormatValid(candidateOtp)) {
            return recordVerificationFailure(ipAddress);
        }

        User user = userOptional.get();

        Optional<UUID> tokenId = tokenService.verify(
                user,
                VerificationTokenPurpose.PASSWORD_RESET,
                candidateOtp
        );

        if (tokenId.isEmpty()) {
            return recordVerificationFailure(ipAddress);
        }

        Optional<VerificationToken> tokenOptional =
                tokenRepository.findById(tokenId.get());

        if (tokenOptional.isEmpty()
                || !isVerifiedResetToken(
                        tokenOptional.get(),
                        user,
                        currentDateTime()
                )) {
            return recordVerificationFailure(ipAddress);
        }

        return PasswordResetVerificationResult.verified(
                tokenId.get()
        );
    }

    @Transactional(readOnly = true)
    public boolean isGrantValid(UUID verifiedTokenId) {
        if (verifiedTokenId == null) {
            return false;
        }

        Optional<VerificationToken> tokenOptional =
                tokenRepository.findById(verifiedTokenId);

        return tokenOptional.isPresent()
                && isVerifiedResetToken(
                        tokenOptional.get(),
                        tokenOptional.get().getUser(),
                        currentDateTime()
                );
    }

    @Transactional
    public PasswordResetCompletionResult completeReset(
            UUID verifiedTokenId,
            String newPassword,
            String confirmPassword
    ) {
        if (verifiedTokenId == null) {
            return PasswordResetCompletionResult.INVALID_GRANT;
        }

        Optional<VerificationToken> tokenOptional =
                tokenRepository.findById(verifiedTokenId);

        if (tokenOptional.isEmpty()) {
            return PasswordResetCompletionResult.INVALID_GRANT;
        }

        VerificationToken token = tokenOptional.get();
        User user = token.getUser();
        LocalDateTime now = currentDateTime();

        if (!isVerifiedResetToken(token, user, now)) {
            return PasswordResetCompletionResult.INVALID_GRANT;
        }

        if (!Objects.equals(newPassword, confirmPassword)) {
            return PasswordResetCompletionResult
                    .PASSWORD_CONFIRMATION_MISMATCH;
        }

        if (!isPasswordValid(newPassword)) {
            return PasswordResetCompletionResult
                    .PASSWORD_POLICY_VIOLATION;
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setAuthVersion(Math.incrementExact(
                user.getAuthVersion()
        ));
        token.setConsumedAt(now);

        userRepository.save(user);
        tokenRepository.saveAndFlush(token);

        return PasswordResetCompletionResult.COMPLETED;
    }

    private PasswordResetVerificationResult
    recordVerificationFailure(String ipAddress) {
        RateLimitDecision failureDecision =
                rateLimitService.recordVerificationFailure(
                        AuthRateLimitAction.VERIFY_RESET,
                        ipAddress
                );

        if (!failureDecision.allowed()) {
            return PasswordResetVerificationResult.rateLimited(
                    failureDecision.blockedUntil()
            );
        }

        return PasswordResetVerificationResult.invalid();
    }

    private boolean isVerifiedResetToken(
            VerificationToken token,
            User expectedUser,
            LocalDateTime now
    ) {
        if (token == null
                || expectedUser == null
                || expectedUser.getId() == null
                || token.getUser() == null
                || token.getUser().getId() == null
                || token.getPurpose()
                != VerificationTokenPurpose.PASSWORD_RESET
                || token.getVerifiedAt() == null
                || token.getConsumedAt() != null
                || !STATUS_ACTIVE.equals(
                        token.getUser().getStatus()
                )) {
            return false;
        }

        if (!Objects.equals(
                token.getUser().getId(),
                expectedUser.getId()
        )) {
            return false;
        }

        LocalDateTime grantExpiresAt =
                token.getVerifiedAt().plus(RESET_GRANT_TTL);

        return now.isBefore(grantExpiresAt);
    }

    private boolean isOtpFormatValid(String candidateOtp) {
        return candidateOtp != null
                && OTP_PATTERN.matcher(candidateOtp).matches();
    }

    private boolean isPasswordValid(String password) {
        if (password == null
                || password.length() < 12
                || password.length() > 64) {
            return false;
        }

        boolean containsLetter = password.codePoints()
                .anyMatch(Character::isLetter);

        boolean containsDigit = password.codePoints()
                .anyMatch(Character::isDigit);

        return containsLetter && containsDigit;
    }

    private String normalizeRequiredEmail(String email) {
        String normalizedEmail = normalizeEmail(email);

        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Email is required"
            );
        }

        return normalizedEmail;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }

        return email.strip().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                clock.getZone()
        );
    }
}