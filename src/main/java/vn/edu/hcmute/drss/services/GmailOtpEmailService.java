package vn.edu.hcmute.drss.services;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.exceptions.OtpMailDeliveryException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class GmailOtpEmailService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GmailOtpEmailService.class);
    private static final String BREVO_EMAIL_URL =
            "https://api.brevo.com/v3/smtp/email";

    private static final Pattern OTP_PATTERN =
            Pattern.compile("[0-9]{6}");

    private final JavaMailSender mailSender;
    private final AuthSecurityProperties properties;
    private final String provider;
    private final String brevoApiKey;
    private final RestClient brevoClient;

    public GmailOtpEmailService(
            JavaMailSender mailSender,
            AuthSecurityProperties properties
    ) {
        this(mailSender, properties, "smtp", "", null);
    }

    @Autowired
    public GmailOtpEmailService(
            JavaMailSender mailSender,
            AuthSecurityProperties properties,
            @Value("${OTP_MAIL_PROVIDER:smtp}") String provider,
            @Value("${BREVO_API_KEY:}") String brevoApiKey
    ) {
        this(
                mailSender,
                properties,
                provider,
                brevoApiKey,
                "brevo".equalsIgnoreCase(provider.strip())
                        ? createBrevoClient()
                        : null
        );
    }

    GmailOtpEmailService(
            JavaMailSender mailSender,
            AuthSecurityProperties properties,
            String provider,
            String brevoApiKey,
            RestClient brevoClient
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.provider = provider.strip().toLowerCase(Locale.ROOT);
        this.brevoApiKey = brevoApiKey == null ? "" : brevoApiKey.strip();
        this.brevoClient = brevoClient;

        if (!"smtp".equals(this.provider) && !"brevo".equals(this.provider)) {
            throw new IllegalArgumentException(
                    "OTP_MAIL_PROVIDER must be smtp or brevo"
            );
        }
    }

    private static RestClient createBrevoClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }

    public void sendOtp(
            String recipient,
            VerificationTokenPurpose purpose,
            String otp
    ) {
        String validatedRecipient = validateRecipient(recipient);
        String validatedOtp = validateOtp(otp);

        if (purpose == null) {
            throw new IllegalArgumentException(
                    "Verification-token purpose is required"
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(properties.mailFrom());
        message.setTo(validatedRecipient);
        message.setSubject(subjectFor(purpose));
        message.setText(bodyFor(
                purpose,
                validatedOtp,
                properties.otpTtl()
        ));

        String attemptId = UUID.randomUUID().toString();
        if ("brevo".equals(provider)) {
            sendThroughBrevo(message, validatedRecipient, purpose, attemptId);
            return;
        }

        if (mailSender instanceof JavaMailSenderImpl smtpSender) {
            LOGGER.info(
                    "OTP_SMTP_START attempt={} purpose={} host={} port={}",
                    attemptId, purpose, smtpSender.getHost(), smtpSender.getPort()
            );
        }
        try {
            mailSender.send(message);
            LOGGER.info("OTP_SMTP_ACCEPTED attempt={} purpose={}",
                    attemptId, purpose);
        } catch (MailException exception) {
            LOGGER.warn(
                    "OTP_SMTP_FAILED attempt={} purpose={} type={} cause={}",
                    attemptId, purpose,
                    exception.getClass().getSimpleName(),
                    exception.getMostSpecificCause().getClass().getSimpleName()
            );
            throw new OtpMailDeliveryException(exception);
        }
    }

    private void sendThroughBrevo(
            SimpleMailMessage message,
            String recipient,
            VerificationTokenPurpose purpose,
            String attemptId
    ) {
        if (brevoApiKey.isBlank() || brevoClient == null) {
            LOGGER.warn("OTP_BREVO_FAILED attempt={} reason=MISSING_CONFIGURATION",
                    attemptId);
            throw new OtpMailDeliveryException(new IllegalStateException(
                    "Brevo HTTPS configuration is missing"
            ));
        }

        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", "DRSS", "email", properties.mailFrom()),
                "to", List.of(Map.of("email", recipient)),
                "subject", message.getSubject(),
                "textContent", message.getText()
        );

        LOGGER.info("OTP_BREVO_START attempt={} purpose={}", attemptId, purpose);
        try {
            ResponseEntity<Void> response = brevoClient.post()
                    .uri(BREVO_EMAIL_URL)
                    .header("api-key", brevoApiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            if (response.getStatusCode().value() != 201) {
                LOGGER.warn("OTP_BREVO_FAILED attempt={} status={}",
                        attemptId, response.getStatusCode().value());
                throw new OtpMailDeliveryException(new IllegalStateException(
                        "Unexpected Brevo HTTPS response"
                ));
            }
            LOGGER.info("OTP_BREVO_ACCEPTED attempt={} purpose={} status=201",
                    attemptId, purpose);
        } catch (RestClientResponseException exception) {
            // Response bodies and full exceptions can contain private data.
            LOGGER.warn("OTP_BREVO_FAILED attempt={} purpose={} status={}",
                    attemptId, purpose, exception.getStatusCode().value());
            throw new OtpMailDeliveryException(exception);
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "OTP_BREVO_FAILED attempt={} purpose={} type={} cause={}",
                    attemptId, purpose,
                    exception.getClass().getSimpleName(),
                    exception.getMostSpecificCause().getClass().getSimpleName()
            );
            throw new OtpMailDeliveryException(exception);
        }
    }

    private String subjectFor(
            VerificationTokenPurpose purpose
    ) {
        return switch (purpose) {
            case ACCOUNT_ACTIVATION ->
                    "Kích hoạt tài khoản DRSS";
            case PASSWORD_RESET ->
                    "Đặt lại mật khẩu DRSS";
        };
    }

    private String bodyFor(
            VerificationTokenPurpose purpose,
            String otp,
            Duration validity
    ) {
        String purposeDescription = switch (purpose) {
            case ACCOUNT_ACTIVATION ->
                    "Bạn đang kích hoạt tài khoản DRSS.";
            case PASSWORD_RESET ->
                    "Bạn đang yêu cầu đặt lại mật khẩu DRSS.";
        };

        long validityMinutes = Math.max(
                1,
                (validity.toSeconds() + 59) / 60
        );

        return purposeDescription
                + System.lineSeparator()
                + System.lineSeparator()
                + "Mã xác thực của bạn là: "
                + otp
                + System.lineSeparator()
                + System.lineSeparator()
                + "Mã có hiệu lực trong "
                + validityMinutes
                + " phút."
                + System.lineSeparator()
                + "Không chia sẻ mã này với bất kỳ ai."
                + System.lineSeparator()
                + System.lineSeparator()
                + "Nếu bạn không thực hiện yêu cầu này, "
                + "hãy bỏ qua email.";
    }

    private String validateRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException(
                    "Recipient email is required"
            );
        }

        String normalizedRecipient = recipient
                .strip()
                .toLowerCase(Locale.ROOT);

        try {
            InternetAddress address = new InternetAddress(
                    normalizedRecipient,
                    true
            );

            address.validate();

            if (!normalizedRecipient.equals(address.getAddress())) {
                throw invalidRecipient();
            }

            return normalizedRecipient;
        } catch (AddressException exception) {
            throw invalidRecipient();
        }
    }

    private String validateOtp(String otp) {
        if (otp == null || !OTP_PATTERN.matcher(otp).matches()) {
            throw new IllegalArgumentException(
                    "OTP must contain exactly six digits"
            );
        }

        return otp;
    }

    private IllegalArgumentException invalidRecipient() {
        return new IllegalArgumentException(
                "Recipient must be a valid email address"
        );
    }
}
