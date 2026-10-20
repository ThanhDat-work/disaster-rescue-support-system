package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.exceptions.OtpMailDeliveryException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GmailOtpEmailServiceTest {

    private static final String OTP = "482731";

    private JavaMailSender mailSender;
    private GmailOtpEmailService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);

        service = new GmailOtpEmailService(
                mailSender,
                properties()
        );
    }

    @Test
    void sendsAccountActivationOtpAsPlainTextEmail() {
        service.sendOtp(
                " Citizen@Example.com ",
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                OTP
        );

        SimpleMailMessage message = captureSentMessage();

        assertThat(message.getFrom())
                .isEqualTo("noreply@drss.example");
        assertThat(message.getTo())
                .containsExactly("citizen@example.com");
        assertThat(message.getSubject())
                .isEqualTo("Kích hoạt tài khoản DRSS");
        assertThat(message.getText())
                .contains(
                        "kích hoạt tài khoản DRSS",
                        OTP,
                        "5 phút",
                        "Không chia sẻ mã này"
                );
    }

    @Test
    void sendsPasswordResetOtpWithDifferentPurposeText() {
        service.sendOtp(
                "citizen@example.com",
                VerificationTokenPurpose.PASSWORD_RESET,
                OTP
        );

        SimpleMailMessage message = captureSentMessage();

        assertThat(message.getSubject())
                .isEqualTo("Đặt lại mật khẩu DRSS");
        assertThat(message.getText())
                .contains(
                        "đặt lại mật khẩu DRSS",
                        OTP,
                        "5 phút"
                )
                .doesNotContain(
                        "kích hoạt tài khoản DRSS"
                );
    }

    @Test
    void rejectsInvalidRecipientWithoutCallingMailSender() {
        assertThatThrownBy(() ->
                service.sendOtp(
                        "not-an-email",
                        VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                        OTP
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Recipient must be a valid email address"
                );

        verify(mailSender, never())
                .send(any(SimpleMailMessage.class));
    }

    @Test
    void rejectsOtpThatIsNotExactlySixDigits() {
        assertThatThrownBy(() ->
                service.sendOtp(
                        "citizen@example.com",
                        VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                        "12A456"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "OTP must contain exactly six digits"
                );

        verify(mailSender, never())
                .send(any(SimpleMailMessage.class));
    }

    @Test
    void rejectsMissingPurposeWithoutSendingEmail() {
        assertThatThrownBy(() ->
                service.sendOtp(
                        "citizen@example.com",
                        null,
                        OTP
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Verification-token purpose is required"
                );

        verify(mailSender, never())
                .send(any(SimpleMailMessage.class));
    }

    @Test
    void wrapsMailFailureWithoutSensitiveDataInPublicMessage() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThatThrownBy(() ->
                service.sendOtp(
                        "citizen@example.com",
                        VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                        OTP
                )
        )
                .isInstanceOf(OtpMailDeliveryException.class)
                .hasMessage("Unable to deliver OTP email")
                .message()
                .doesNotContain(
                        OTP,
                        "citizen@example.com",
                        "gmail",
                        "password"
                );
    }

    private SimpleMailMessage captureSentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        return captor.getValue();
    }

    private AuthSecurityProperties properties() {
        return new AuthSecurityProperties(
                "test-jwt-secret-at-least-32-bytes-aaaa",
                "test-otp-secret-at-least-32-bytes-bbbb",
                "test-rate-limit-secret-at-least-32-cccc",
                "drss",
                Duration.ofMinutes(30),
                "DRSS_ACCESS_TOKEN",
                false,
                Duration.ofMinutes(5),
                5,
                "http://localhost:8080",
                "noreply@drss.example"
        );
    }
}