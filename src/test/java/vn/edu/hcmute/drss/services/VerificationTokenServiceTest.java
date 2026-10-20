package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.VerificationToken;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.repositories.VerificationTokenRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationTokenServiceTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID TOKEN_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final Instant CURRENT_INSTANT =
            Instant.parse("2026-10-19T05:00:00Z");

    private static final String TOKEN_DIGEST =
            "0123456789abcdef0123456789abcdef"
                    + "0123456789abcdef0123456789abcdef";

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private OtpCodeService otpCodeService;

    @Mock
    private AuthSecurityProperties properties;

    @Mock
    private User user;

    private VerificationTokenService verificationTokenService;
    private LocalDateTime currentDateTime;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                CURRENT_INSTANT,
                ZoneOffset.UTC
        );

        currentDateTime = LocalDateTime.ofInstant(
                CURRENT_INSTANT,
                ZoneId.systemDefault()
        );

        verificationTokenService = new VerificationTokenService(
                verificationTokenRepository,
                otpCodeService,
                properties,
                fixedClock
        );

        when(user.getId()).thenReturn(USER_ID);
    }

    @Test
    void issueInvalidatesPreviousTokensAndStoresOnlyDigest() {
        VerificationToken previousToken = new VerificationToken();

        when(verificationTokenRepository
                .findAllByUserAndPurposeAndConsumedAtIsNull(
                        user,
                        VerificationTokenPurpose.ACCOUNT_ACTIVATION
                ))
                .thenReturn(List.of(previousToken));

        when(otpCodeService.generate()).thenReturn("012345");

        when(otpCodeService.digest(
                USER_ID,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "012345"
        )).thenReturn(TOKEN_DIGEST);

        when(properties.otpTtl()).thenReturn(Duration.ofMinutes(5));
        when(properties.otpMaxAttempts()).thenReturn(5);

        when(verificationTokenRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IssuedOtp issuedOtp = verificationTokenService.issue(
                user,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION
        );

        assertThat(previousToken.getConsumedAt())
                .isEqualTo(currentDateTime);

        ArgumentCaptor<VerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(VerificationToken.class);

        verify(verificationTokenRepository)
                .saveAndFlush(tokenCaptor.capture());

        VerificationToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getUser()).isSameAs(user);
        assertThat(savedToken.getPurpose())
                .isEqualTo(
                        VerificationTokenPurpose.ACCOUNT_ACTIVATION
                );
        assertThat(savedToken.getTokenDigest())
                .isEqualTo(TOKEN_DIGEST)
                .isNotEqualTo("012345");
        assertThat(savedToken.getExpiresAt())
                .isEqualTo(currentDateTime.plusMinutes(5));
        assertThat(savedToken.getFailedAttempts()).isZero();
        assertThat(savedToken.getMaxAttempts()).isEqualTo(5);
        assertThat(savedToken.getVerifiedAt()).isNull();
        assertThat(savedToken.getConsumedAt()).isNull();

        assertThat(issuedOtp.code()).isEqualTo("012345");
        assertThat(issuedOtp.expiresAt())
                .isEqualTo(currentDateTime.plusMinutes(5));
        assertThat(issuedOtp.toString())
                .contains("<redacted>")
                .doesNotContain("012345");

        verify(verificationTokenRepository)
                .saveAll(List.of(previousToken));
    }

    @Test
    void correctOtpMarksTokenVerifiedButDoesNotConsumeIt() {
        VerificationToken token = activeToken();

        when(verificationTokenRepository
                .findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        user,
                        VerificationTokenPurpose.PASSWORD_RESET
                ))
                .thenReturn(Optional.of(token));

        when(otpCodeService.matches(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456",
                TOKEN_DIGEST
        )).thenReturn(true);

        Optional<UUID> result = verificationTokenService.verify(
                user,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456"
        );

        assertThat(result).contains(TOKEN_ID);
        assertThat(token.getVerifiedAt()).isEqualTo(currentDateTime);
        assertThat(token.getConsumedAt()).isNull();
        assertThat(token.getFailedAttempts()).isZero();

        verify(verificationTokenRepository).saveAndFlush(token);
    }

    @Test
    void incorrectOtpIncrementsFailedAttempts() {
        VerificationToken token = activeToken();

        when(verificationTokenRepository
                .findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        user,
                        VerificationTokenPurpose.PASSWORD_RESET
                ))
                .thenReturn(Optional.of(token));

        when(otpCodeService.matches(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                "654321",
                TOKEN_DIGEST
        )).thenReturn(false);

        Optional<UUID> result = verificationTokenService.verify(
                user,
                VerificationTokenPurpose.PASSWORD_RESET,
                "654321"
        );

        assertThat(result).isEmpty();
        assertThat(token.getFailedAttempts()).isEqualTo(1);
        assertThat(token.getVerifiedAt()).isNull();

        verify(verificationTokenRepository).saveAndFlush(token);
    }

    @Test
    void fifthFailureExhaustsTokenAndFurtherAttemptsAreRejected() {
        VerificationToken token = activeToken();
        token.setFailedAttempts(4);

        when(verificationTokenRepository
                .findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        user,
                        VerificationTokenPurpose.PASSWORD_RESET
                ))
                .thenReturn(Optional.of(token));

        when(otpCodeService.matches(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                "654321",
                TOKEN_DIGEST
        )).thenReturn(false);

        Optional<UUID> fifthAttempt = verificationTokenService.verify(
                user,
                VerificationTokenPurpose.PASSWORD_RESET,
                "654321"
        );

        Optional<UUID> laterAttempt = verificationTokenService.verify(
                user,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456"
        );

        assertThat(fifthAttempt).isEmpty();
        assertThat(laterAttempt).isEmpty();
        assertThat(token.getFailedAttempts()).isEqualTo(5);

        verify(otpCodeService, times(1)).matches(
                USER_ID,
                VerificationTokenPurpose.PASSWORD_RESET,
                "654321",
                TOKEN_DIGEST
        );

        verify(verificationTokenRepository, times(1))
                .saveAndFlush(token);
    }

    @Test
    void expiredOtpIsRejectedWithoutChangingAttempts() {
        VerificationToken token = activeToken();
        token.setExpiresAt(currentDateTime);

        when(verificationTokenRepository
                .findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        user,
                        VerificationTokenPurpose.PASSWORD_RESET
                ))
                .thenReturn(Optional.of(token));

        Optional<UUID> result = verificationTokenService.verify(
                user,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456"
        );

        assertThat(result).isEmpty();
        assertThat(token.getFailedAttempts()).isZero();

        verifyNoInteractions(otpCodeService);
        verify(verificationTokenRepository, never())
                .saveAndFlush(any());
    }

    @Test
    void alreadyVerifiedTokenCannotGrantAnotherSession() {
        VerificationToken token = activeToken();
        token.setVerifiedAt(currentDateTime.minusMinutes(1));

        when(verificationTokenRepository
                .findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        user,
                        VerificationTokenPurpose.PASSWORD_RESET
                ))
                .thenReturn(Optional.of(token));

        Optional<UUID> result = verificationTokenService.verify(
                user,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456"
        );

        assertThat(result).isEmpty();

        verifyNoInteractions(otpCodeService);
        verify(verificationTokenRepository, never())
                .saveAndFlush(any());
    }

    @Test
    void missingActiveTokenReturnsGenericFailure() {
        when(verificationTokenRepository
                .findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        user,
                        VerificationTokenPurpose.PASSWORD_RESET
                ))
                .thenReturn(Optional.empty());

        Optional<UUID> result = verificationTokenService.verify(
                user,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456"
        );

        assertThat(result).isEmpty();

        verifyNoInteractions(otpCodeService);
        verify(verificationTokenRepository, never())
                .saveAndFlush(any());
    }

    private VerificationToken activeToken() {
        VerificationToken token = new VerificationToken();
        token.setId(TOKEN_ID);
        token.setUser(user);
        token.setPurpose(VerificationTokenPurpose.PASSWORD_RESET);
        token.setTokenDigest(TOKEN_DIGEST);
        token.setExpiresAt(currentDateTime.plusMinutes(5));
        token.setFailedAttempts(0);
        token.setMaxAttempts(5);
        return token;
    }
}