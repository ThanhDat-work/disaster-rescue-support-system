package vn.edu.hcmute.drss.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.exceptions.OtpMailDeliveryException;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class GmailOtpEmailService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GmailOtpEmailService.class);

    private static final Pattern OTP_PATTERN =
            Pattern.compile("[0-9]{6}");

    private final JavaMailSender mailSender;
    private final AuthSecurityProperties properties;

    public GmailOtpEmailService(
            JavaMailSender mailSender,
            AuthSecurityProperties properties
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
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
        long startedAt = System.nanoTime();

        if (mailSender instanceof JavaMailSenderImpl smtpSender) {
            LOGGER.info(
                    "OTP_SMTP_START attempt={} purpose={} host={} port={}",
                    attemptId,
                    purpose,
                    smtpSender.getHost(),
                    smtpSender.getPort()
            );
        } else {
            LOGGER.info(
                    "OTP_SMTP_START attempt={} purpose={} senderType={}",
                    attemptId,
                    purpose,
                    mailSender.getClass().getSimpleName()
            );
        }

        try {
            mailSender.send(message);
            LOGGER.info(
                    "OTP_SMTP_ACCEPTED attempt={} purpose={} elapsedMs={}",
                    attemptId,
                    purpose,
                    TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startedAt
                    )
            );
        } catch (MailException exception) {
            // Log only exception types. Exception messages and failed
            // mail objects can contain recipient addresses or OTPs.
            LOGGER.warn(
                    "OTP_SMTP_FAILED attempt={} purpose={} elapsedMs={} types={}",
                    attemptId,
                    purpose,
                    TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startedAt
                    ),
                    failureTypes(exception)
            );
            throw new OtpMailDeliveryException(exception);
        }
    }

    private String failureTypes(MailException exception) {
        Set<Throwable> visited = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        List<String> types = new ArrayList<>();
        pending.add(exception);

        while (!pending.isEmpty() && visited.size() < 16) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            types.add(current.getClass().getSimpleName());

            if (current.getCause() != null) {
                pending.addLast(current.getCause());
            }
            if (current instanceof MessagingException mailFailure
                    && mailFailure.getNextException() != null) {
                pending.addLast(mailFailure.getNextException());
            }
            if (current instanceof MailSendException sendFailure) {
                for (Exception failure : sendFailure.getMessageExceptions()) {
                    if (failure != null) {
                        pending.addLast(failure);
                    }
                }
            }
        }

        return String.join(" -> ", types);
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