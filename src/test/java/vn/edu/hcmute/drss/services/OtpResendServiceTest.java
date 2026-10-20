package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.repositories.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpResendServiceTest {

    private static final String EMAIL = "citizen@example.com";
    private static final String IP = "203.0.113.20";

    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationTokenService tokenService;
    @Mock
    private AuthRateLimitService rateLimitService;
    @Mock
    private GmailOtpEmailService emailService;

    private OtpResendService service;

    @BeforeEach
    void setUp() {
        service = new OtpResendService(
                userRepository,
                tokenService,
                rateLimitService,
                emailService
        );
    }

    @Test
    void inactiveUserCanReceiveNewActivationOtp() {
        User user = userWithStatus("INACTIVE");
        allowRequest();
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));
        when(tokenService.issue(
                user,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION
        )).thenReturn(new IssuedOtp(
                "123456",
                LocalDateTime.now().plusMinutes(5)
        ));

        OtpResendResult result = service.resend(
                EMAIL,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                IP
        );

        assertThat(result.status())
                .isEqualTo(OtpResendResult.Status.ACCEPTED);
        verify(emailService).sendOtp(
                EMAIL,
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "123456"
        );
    }

    @Test
    void activeUserCanReceiveNewPasswordResetOtp() {
        User user = userWithStatus("ACTIVE");
        allowRequest();
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(user));
        when(tokenService.issue(
                user,
                VerificationTokenPurpose.PASSWORD_RESET
        )).thenReturn(new IssuedOtp(
                "654321",
                LocalDateTime.now().plusMinutes(5)
        ));

        OtpResendResult result = service.resend(
                EMAIL,
                VerificationTokenPurpose.PASSWORD_RESET,
                IP
        );

        assertThat(result.status())
                .isEqualTo(OtpResendResult.Status.ACCEPTED);
        verify(emailService).sendOtp(
                EMAIL,
                VerificationTokenPurpose.PASSWORD_RESET,
                "654321"
        );
    }

    @Test
    void unknownEmailGetsSameAcceptedResultWithoutIssuingOtp() {
        allowRequest();
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.empty());

        OtpResendResult result = service.resend(
                EMAIL,
                VerificationTokenPurpose.PASSWORD_RESET,
                IP
        );

        assertThat(result.status())
                .isEqualTo(OtpResendResult.Status.ACCEPTED);
        verifyNoInteractions(tokenService, emailService);
    }

    @Test
    void rateLimitStopsBeforeAccountLookup() {
        LocalDateTime blockedUntil =
                LocalDateTime.now().plusMinutes(1);

        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.RESEND_OTP,
                EMAIL,
                IP
        )).thenReturn(
                RateLimitDecision.blockedUntil(blockedUntil)
        );

        OtpResendResult result = service.resend(
                EMAIL,
                VerificationTokenPurpose.PASSWORD_RESET,
                IP
        );

        assertThat(result.status())
                .isEqualTo(OtpResendResult.Status.RATE_LIMITED);
        assertThat(result.blockedUntil()).isEqualTo(blockedUntil);
        verify(userRepository, never())
                .findByEmailIgnoreCase(EMAIL);
        verifyNoInteractions(tokenService, emailService);
    }

    private void allowRequest() {
        when(rateLimitService.consumePublicRequest(
                AuthRateLimitAction.RESEND_OTP,
                EMAIL,
                IP
        )).thenReturn(RateLimitDecision.allowedDecision());
    }

    private User userWithStatus(String status) {
        User user = new User();
        user.setEmail(EMAIL);
        user.setStatus(status);
        return user;
    }
}