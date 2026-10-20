package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.edu.hcmute.drss.dtos.RegistrationRequest;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.exceptions.OtpMailDeliveryException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountRegistrationServiceTest {

    private static final String IP_ADDRESS = "203.0.113.10";

    private AuthRateLimitService rateLimitService;
    private RegistrationPersistenceService persistenceService;
    private GmailOtpEmailService emailService;
    private AccountRegistrationService service;
    private RegistrationRequest request;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(AuthRateLimitService.class);
        persistenceService = mock(
                RegistrationPersistenceService.class
        );
        emailService = mock(GmailOtpEmailService.class);

        service = new AccountRegistrationService(
                rateLimitService,
                persistenceService,
                emailService
        );

        request = new RegistrationRequest(
                "Nguyen Van Dan",
                "citizen@example.com",
                "0912345678",
                "secure password 123",
                "secure password 123"
        );
    }

    @Test
    void persistedRegistrationSendsActivationOtp() {
        PendingOtpDelivery delivery =
                new PendingOtpDelivery(
                        request.email(),
                        "123456"
                );

        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.REGISTER,
                request.email(),
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        when(persistenceService.prepareRegistration(request))
                .thenReturn(Optional.of(delivery));

        RegistrationResult result =
                service.register(request, IP_ADDRESS);

        assertThat(result.status())
                .isEqualTo(
                        RegistrationResult.Status.ACCEPTED
                );

        verify(emailService).sendOtp(
                request.email(),
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "123456"
        );
    }

    @Test
    void mailFailureDoesNotRepeatPersistenceOperation() {
        PendingOtpDelivery delivery =
                new PendingOtpDelivery(
                        request.email(),
                        "123456"
                );

        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.REGISTER,
                request.email(),
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        when(persistenceService.prepareRegistration(request))
                .thenReturn(Optional.of(delivery));

        doThrow(new OtpMailDeliveryException(
                new RuntimeException("SMTP unavailable")
        )).when(emailService).sendOtp(
                request.email(),
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "123456"
        );

        RegistrationResult result =
                service.register(request, IP_ADDRESS);

        assertThat(result.status()).isEqualTo(
                RegistrationResult.Status.EMAIL_DELIVERY_FAILED
        );

        verify(persistenceService)
                .prepareRegistration(request);
    }

    @Test
    void existingProtectedAccountReturnsAcceptedWithoutMail() {
        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.REGISTER,
                request.email(),
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        when(persistenceService.prepareRegistration(request))
                .thenReturn(Optional.empty());

        RegistrationResult result =
                service.register(request, IP_ADDRESS);

        assertThat(result.status())
                .isEqualTo(
                        RegistrationResult.Status.ACCEPTED
                );

        verify(emailService, never())
                .sendOtp(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    void blockedRegistrationDoesNotTouchPersistence() {
        LocalDateTime blockedUntil =
                LocalDateTime.of(
                        2026,
                        10,
                        19,
                        8,
                        15
                );

        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.REGISTER,
                request.email(),
                IP_ADDRESS
        )).thenReturn(
                RateLimitDecision.blockedUntil(blockedUntil)
        );

        RegistrationResult result =
                service.register(request, IP_ADDRESS);

        assertThat(result.status()).isEqualTo(
                RegistrationResult.Status.RATE_LIMITED
        );
        assertThat(result.retryAfter())
                .isEqualTo(blockedUntil);

        verify(persistenceService, never())
                .prepareRegistration(request);
    }

    @Test
    void duplicateEmailRaceStillReturnsGenericAcceptedResult() {
        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.REGISTER,
                request.email(),
                IP_ADDRESS
        )).thenReturn(RateLimitDecision.allowedDecision());

        when(persistenceService.prepareRegistration(request))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "duplicate email"
                        )
                );

        RegistrationResult result =
                service.register(request, IP_ADDRESS);

        assertThat(result.status())
                .isEqualTo(
                        RegistrationResult.Status.ACCEPTED
                );

        verify(emailService, never())
                .sendOtp(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }
}