package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.VerificationToken;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.VerificationTokenRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountActivationServiceTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID TOKEN_ID =
            UUID.fromString(
                    "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
            );

    private static final String EMAIL =
            "citizen@example.com";

    private static final String IP_ADDRESS =
            "203.0.113.10";

    private UserRepository userRepository;
    private VerificationTokenRepository tokenRepository;
    private VerificationTokenService tokenService;
    private AuthRateLimitService rateLimitService;
    private AccountActivationService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(
                VerificationTokenRepository.class
        );
        tokenService = mock(VerificationTokenService.class);
        rateLimitService = mock(AuthRateLimitService.class);

        service = new AccountActivationService(
                userRepository,
                tokenRepository,
                tokenService,
                rateLimitService,
                Clock.fixed(
                        Instant.parse("2026-10-19T07:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        user.setStatus("INACTIVE");

        when(rateLimitService.checkVerificationAllowed(
                AuthRateLimitAction.VERIFY_ACTIVATION,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());
    }

    @Test
    void correctOtpActivatesUserAndConsumesToken() {
        VerificationToken token = validVerifiedToken();

        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));

        when(tokenService.verify(
                user,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "123456"
        )).thenReturn(Optional.of(TOKEN_ID));

        when(tokenRepository.findById(TOKEN_ID))
                .thenReturn(Optional.of(token));

        ActivationResult result = service.activate(
                EMAIL,
                "123456",
                IP_ADDRESS
        );

        assertThat(result.status())
                .isEqualTo(
                        ActivationResult.Status.ACTIVATED
                );
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(token.getConsumedAt()).isNotNull();

        verify(userRepository).saveAndFlush(user);
        verify(tokenRepository).saveAndFlush(token);

        verify(rateLimitService, never())
                .recordVerificationFailure(
                        any(),
                        any()
                );
    }

    @Test
    void incorrectOtpReturnsGenericFailureAndCountsIpFailure() {
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));

        when(tokenService.verify(
                user,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "654321"
        )).thenReturn(Optional.empty());

        when(rateLimitService.recordVerificationFailure(
                AuthRateLimitAction.VERIFY_ACTIVATION,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        ActivationResult result = service.activate(
                EMAIL,
                "654321",
                IP_ADDRESS
        );

        assertThat(result.status())
                .isEqualTo(ActivationResult.Status.INVALID);
        assertThat(user.getStatus()).isEqualTo("INACTIVE");

        verify(userRepository, never()).saveAndFlush(any());
        verify(tokenRepository, never()).saveAndFlush(any());
    }

    @Test
    void missingAccountUsesSameGenericFailure() {
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.empty());

        when(rateLimitService.recordVerificationFailure(
                AuthRateLimitAction.VERIFY_ACTIVATION,
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        ActivationResult result = service.activate(
                EMAIL,
                "123456",
                IP_ADDRESS
        );

        assertThat(result.status())
                .isEqualTo(ActivationResult.Status.INVALID);

        verify(tokenService, never())
                .verify(any(), any(), any());
    }

    @Test
    void activeIpBlockStopsVerificationBeforeAccountLookup() {
        LocalDateTime blockedUntil =
                LocalDateTime.of(
                        2026,
                        10,
                        19,
                        8,
                        15
                );

        when(rateLimitService.checkVerificationAllowed(
                AuthRateLimitAction.VERIFY_ACTIVATION,
                IP_ADDRESS
        )).thenReturn(
                RateLimitDecision.blockedUntil(blockedUntil)
        );

        ActivationResult result = service.activate(
                EMAIL,
                "123456",
                IP_ADDRESS
        );

        assertThat(result.status()).isEqualTo(
                ActivationResult.Status.RATE_LIMITED
        );
        assertThat(result.retryAfter())
                .isEqualTo(blockedUntil);

        verify(userRepository, never())
                .findByEmailIgnoreCase(any());
        verify(tokenService, never())
                .verify(any(), any(), any());
    }

    private VerificationToken validVerifiedToken() {
        VerificationToken token = new VerificationToken();

        token.setId(TOKEN_ID);
        token.setUser(user);
        token.setPurpose(
                VerificationTokenPurpose.ACCOUNT_ACTIVATION
        );
        token.setVerifiedAt(
                LocalDateTime.ofInstant(
                        Instant.parse("2026-10-19T07:00:00Z"),
                        ZoneId.systemDefault()
                )
        );
        token.setConsumedAt(null);

        return token;
    }
}