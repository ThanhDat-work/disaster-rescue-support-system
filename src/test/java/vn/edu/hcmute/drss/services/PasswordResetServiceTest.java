package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.VerificationToken;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.exceptions.OtpMailDeliveryException;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.VerificationTokenRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String EMAIL = "citizen@example.com";
    private static final String IP_ADDRESS = "203.0.113.10";
    private static final String VALID_PASSWORD =
            "new secure password 123";

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID TOKEN_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );
    private static final Instant CURRENT_INSTANT =
            Instant.parse("2026-10-19T12:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository tokenRepository;

    @Mock
    private VerificationTokenService tokenService;

    @Mock
    private AuthRateLimitService rateLimitService;

    @Mock
    private GmailOtpEmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                CURRENT_INSTANT,
                ZoneOffset.UTC
        );

        now = LocalDateTime.ofInstant(
                CURRENT_INSTANT,
                ZoneOffset.UTC
        );

        service = new PasswordResetService(
                userRepository,
                tokenRepository,
                tokenService,
                rateLimitService,
                emailService,
                passwordEncoder,
                clock
        );
    }

    @Test
    void requestResetIssuesAndSendsOtpForActiveUser() {
        User user = activeUser();

        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.FORGOT_PASSWORD,
                EMAIL,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));

        when(tokenService.issue(
                user,
                VerificationTokenPurpose.PASSWORD_RESET
        )).thenReturn(new IssuedOtp(
                "123456",
                now.plusMinutes(5)
        ));

        PasswordResetRequestResult result =
                service.requestReset(
                        "  Citizen@Example.COM  ",
                        IP_ADDRESS
                );

        assertThat(result.status()).isEqualTo(
                PasswordResetRequestResult.Status.ACCEPTED
        );

        verify(emailService).sendOtp(
                EMAIL,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456"
        );
    }

    @Test
    void requestResetUsesSameAcceptedResultForUnknownEmail() {
        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.FORGOT_PASSWORD,
                EMAIL,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.empty());

        PasswordResetRequestResult result =
                service.requestReset(EMAIL, IP_ADDRESS);

        assertThat(result.status()).isEqualTo(
                PasswordResetRequestResult.Status.ACCEPTED
        );

        verifyNoInteractions(tokenService, emailService);
    }

    @Test
    void mailFailureDoesNotChangeTheExternalResult() {
        User user = activeUser();

        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.FORGOT_PASSWORD,
                EMAIL,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));

        when(tokenService.issue(
                user,
                VerificationTokenPurpose.PASSWORD_RESET
        )).thenReturn(new IssuedOtp(
                "123456",
                now.plusMinutes(5)
        ));

        doThrow(OtpMailDeliveryException.class)
                .when(emailService)
                .sendOtp(
                        EMAIL,
                        VerificationTokenPurpose.PASSWORD_RESET,
                        "123456"
                );

        PasswordResetRequestResult result =
                service.requestReset(EMAIL, IP_ADDRESS);

        assertThat(result.status()).isEqualTo(
                PasswordResetRequestResult.Status.ACCEPTED
        );
    }

    @Test
    void requestResetStopsBeforeLookupWhenRateLimited() {
        LocalDateTime blockedUntil = now.plusMinutes(15);

        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.FORGOT_PASSWORD,
                EMAIL,
                IP_ADDRESS
        )).thenReturn(
                RateLimitDecision.blockedUntil(blockedUntil)
        );

        PasswordResetRequestResult result =
                service.requestReset(EMAIL, IP_ADDRESS);

        assertThat(result.status()).isEqualTo(
                PasswordResetRequestResult.Status.RATE_LIMITED
        );
        assertThat(result.blockedUntil()).isEqualTo(blockedUntil);

        verifyNoInteractions(
                userRepository,
                tokenService,
                emailService
        );
    }

    @Test
    void correctResetOtpReturnsVerifiedTokenId() {
        User user = activeUser();
        VerificationToken token = verifiedToken(user);

        when(rateLimitService.checkVerificationAllowed(
                AuthRateLimitAction.VERIFY_RESET,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));

        when(tokenService.verify(
                user,
                VerificationTokenPurpose.PASSWORD_RESET,
                "123456"
        )).thenReturn(Optional.of(TOKEN_ID));

        when(tokenRepository.findById(TOKEN_ID))
                .thenReturn(Optional.of(token));

        PasswordResetVerificationResult result =
                service.verifyResetOtp(
                        EMAIL,
                        "123456",
                        IP_ADDRESS
                );

        assertThat(result.status()).isEqualTo(
                PasswordResetVerificationResult.Status.VERIFIED
        );
        assertThat(result.verifiedTokenId()).isEqualTo(TOKEN_ID);

        verify(rateLimitService, never())
                .recordVerificationFailure(
                        AuthRateLimitAction.VERIFY_RESET,
                        IP_ADDRESS
                );
    }

    @Test
    void invalidResetOtpRecordsGenericIpFailure() {
        User user = activeUser();

        when(rateLimitService.checkVerificationAllowed(
                AuthRateLimitAction.VERIFY_RESET,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));

        when(tokenService.verify(
                user,
                VerificationTokenPurpose.PASSWORD_RESET,
                "654321"
        )).thenReturn(Optional.empty());

        when(rateLimitService.recordVerificationFailure(
                AuthRateLimitAction.VERIFY_RESET,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        PasswordResetVerificationResult result =
                service.verifyResetOtp(
                        EMAIL,
                        "654321",
                        IP_ADDRESS
                );

        assertThat(result.status()).isEqualTo(
                PasswordResetVerificationResult.Status.INVALID
        );
    }

    @Test
    void grantExpiresTenMinutesAfterVerification() {
        User user = activeUser();
        VerificationToken token = verifiedToken(user);
        token.setVerifiedAt(now.minusMinutes(10));

        when(tokenRepository.findById(TOKEN_ID))
                .thenReturn(Optional.of(token));

        assertThat(service.isGrantValid(TOKEN_ID)).isFalse();
    }

    @Test
    void completeResetUpdatesPasswordVersionAndTokenAtomically() {
        User user = activeUser();
        user.setAuthVersion(7);

        VerificationToken token = verifiedToken(user);

        when(tokenRepository.findById(TOKEN_ID))
                .thenReturn(Optional.of(token));

        when(passwordEncoder.encode(VALID_PASSWORD))
                .thenReturn("new-bcrypt-hash");

        PasswordResetCompletionResult result =
                service.completeReset(
                        TOKEN_ID,
                        VALID_PASSWORD,
                        VALID_PASSWORD
                );

        assertThat(result).isEqualTo(
                PasswordResetCompletionResult.COMPLETED
        );
        assertThat(user.getPasswordHash())
                .isEqualTo("new-bcrypt-hash");
        assertThat(user.getAuthVersion()).isEqualTo(8);
        assertThat(token.getConsumedAt()).isEqualTo(now);

        verify(userRepository).save(user);
        verify(tokenRepository).saveAndFlush(token);
    }

    @Test
    void passwordMismatchDoesNotMutatePersistence() {
        User user = activeUser();
        VerificationToken token = verifiedToken(user);

        when(tokenRepository.findById(TOKEN_ID))
                .thenReturn(Optional.of(token));

        PasswordResetCompletionResult result =
                service.completeReset(
                        TOKEN_ID,
                        VALID_PASSWORD,
                        "different password 456"
                );

        assertThat(result).isEqualTo(
                PasswordResetCompletionResult
                        .PASSWORD_CONFIRMATION_MISMATCH
        );
        assertThat(token.getConsumedAt()).isNull();

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(user);
        verify(tokenRepository, never()).saveAndFlush(token);
    }

    @Test
    void weakPasswordDoesNotMutatePersistence() {
        User user = activeUser();
        VerificationToken token = verifiedToken(user);

        when(tokenRepository.findById(TOKEN_ID))
                .thenReturn(Optional.of(token));

        PasswordResetCompletionResult result =
                service.completeReset(
                        TOKEN_ID,
                        "onlyletters",
                        "onlyletters"
                );

        assertThat(result).isEqualTo(
                PasswordResetCompletionResult
                        .PASSWORD_POLICY_VIOLATION
        );
        assertThat(token.getConsumedAt()).isNull();

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(user);
        verify(tokenRepository, never()).saveAndFlush(token);
    }

    @Test
    void consumedTokenCannotResetPasswordAgain() {
        User user = activeUser();
        VerificationToken token = verifiedToken(user);
        token.setConsumedAt(now.minusMinutes(1));

        when(tokenRepository.findById(TOKEN_ID))
                .thenReturn(Optional.of(token));

        PasswordResetCompletionResult result =
                service.completeReset(
                        TOKEN_ID,
                        VALID_PASSWORD,
                        VALID_PASSWORD
                );

        assertThat(result).isEqualTo(
                PasswordResetCompletionResult.INVALID_GRANT
        );

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(user);
        verify(tokenRepository, never()).saveAndFlush(token);
    }

    private User activeUser() {
        User user = new User();

        user.setId(USER_ID);
        user.setEmail(EMAIL);
        user.setStatus("ACTIVE");
        user.setPasswordHash("old-bcrypt-hash");
        user.setAuthVersion(0);

        return user;
    }

    private VerificationToken verifiedToken(User user) {
        VerificationToken token = new VerificationToken();

        token.setId(TOKEN_ID);
        token.setUser(user);
        token.setPurpose(VerificationTokenPurpose.PASSWORD_RESET);
        token.setVerifiedAt(now.minusMinutes(1));
        token.setConsumedAt(null);

        return token;
    }
}