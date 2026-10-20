package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.VerificationToken;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.repositories.VerificationTokenRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class VerificationTokenService {

    private final VerificationTokenRepository verificationTokenRepository;
    private final OtpCodeService otpCodeService;
    private final AuthSecurityProperties properties;
    private final Clock clock;

    public VerificationTokenService(
            VerificationTokenRepository verificationTokenRepository,
            OtpCodeService otpCodeService,
            AuthSecurityProperties properties,
            Clock clock
    ) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.otpCodeService = otpCodeService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedOtp issue(
            User user,
            VerificationTokenPurpose purpose
    ) {
        validateUser(user);
        Objects.requireNonNull(purpose, "OTP purpose is required");

        LocalDateTime now = currentDateTime();

        List<VerificationToken> previousTokens =
                verificationTokenRepository
                        .findAllByUserAndPurposeAndConsumedAtIsNull(
                                user,
                                purpose
                        );

        if (!previousTokens.isEmpty()) {
            previousTokens.forEach(token -> token.setConsumedAt(now));
            verificationTokenRepository.saveAll(previousTokens);
        }

        String otp = otpCodeService.generate();

        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setPurpose(purpose);
        token.setTokenDigest(
                otpCodeService.digest(user.getId(), purpose, otp)
        );
        token.setExpiresAt(now.plus(properties.otpTtl()));
        token.setFailedAttempts(0);
        token.setMaxAttempts(properties.otpMaxAttempts());

        verificationTokenRepository.saveAndFlush(token);

        return new IssuedOtp(otp, token.getExpiresAt());
    }

    @Transactional
    public Optional<UUID> verify(
            User user,
            VerificationTokenPurpose purpose,
            String candidateOtp
    ) {
        validateUser(user);
        Objects.requireNonNull(purpose, "OTP purpose is required");

        Optional<VerificationToken> tokenOptional =
                verificationTokenRepository
                        .findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                                user,
                                purpose
                        );

        if (tokenOptional.isEmpty()) {
            return Optional.empty();
        }

        VerificationToken token = tokenOptional.get();
        LocalDateTime now = currentDateTime();

        if (token.getVerifiedAt() != null) {
            return Optional.empty();
        }

        if (!now.isBefore(token.getExpiresAt())) {
            return Optional.empty();
        }

        if (token.getFailedAttempts() >= token.getMaxAttempts()) {
            return Optional.empty();
        }

        boolean matches = otpCodeService.matches(
                user.getId(),
                purpose,
                candidateOtp,
                token.getTokenDigest()
        );

        if (!matches) {
            token.setFailedAttempts(token.getFailedAttempts() + 1);
            verificationTokenRepository.saveAndFlush(token);
            return Optional.empty();
        }

        token.setVerifiedAt(now);
        verificationTokenRepository.saveAndFlush(token);

        return Optional.of(token.getId());
    }

    private void validateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(
                    "A persisted user is required"
            );
        }
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneId.systemDefault()
        );
    }
}