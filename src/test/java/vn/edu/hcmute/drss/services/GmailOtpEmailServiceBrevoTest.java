package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.exceptions.OtpMailDeliveryException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GmailOtpEmailServiceBrevoTest {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";
    private static final String TEST_KEY = "test-only-not-a-real-api-key";
    private JavaMailSender smtpSender;
    private RestClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        smtpSender = mock(JavaMailSender.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
    }

    @Test
    void brevoProviderPostsOtpAsJsonAndDoesNotCallSmtp() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", TEST_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "sender": {"name":"DRSS","email":"noreply@drss.example"},
                          "to": [{"email":"citizen@example.com"}],
                          "subject": "Kích hoạt tài khoản DRSS"
                        }
                        """))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("482731")))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"test-message-id\"}"));

        service(TEST_KEY).sendOtp(" Citizen@Example.com ",
                VerificationTokenPurpose.ACCOUNT_ACTIVATION, "482731");

        server.verify();
        verifyNoInteractions(smtpSender);
    }

    @Test
    void rejectedApiCallReturnsSafeDeliveryFailureWithoutSmtpFallback() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"unauthorized\",\"message\":\"API key rejected\"}"));

        assertThatThrownBy(() -> service(TEST_KEY).sendOtp(
                "citizen@example.com",
                VerificationTokenPurpose.ACCOUNT_ACTIVATION,
                "482731"
        )).isInstanceOf(OtpMailDeliveryException.class)
                .hasMessage("Unable to deliver OTP email");

        server.verify();
        verifyNoInteractions(smtpSender);
    }

    @Test
    void missingApiKeyMakesNoHttpOrSmtpCall() {
        assertThatThrownBy(() -> service("").sendOtp(
                "citizen@example.com",
                VerificationTokenPurpose.PASSWORD_RESET,
                "482731"
        )).isInstanceOf(OtpMailDeliveryException.class)
                .hasMessage("Unable to deliver OTP email");

        server.verify();
        verifyNoInteractions(smtpSender);
    }

    private GmailOtpEmailService service(String apiKey) {
        return new GmailOtpEmailService(
                smtpSender,
                new AuthSecurityProperties(
                        "test-jwt-secret-at-least-32-bytes-aaaa",
                        "test-otp-secret-at-least-32-bytes-bbbb",
                        "test-rate-limit-secret-at-least-32-cccc",
                        "drss", Duration.ofMinutes(30), "DRSS_ACCESS_TOKEN", false,
                        Duration.ofMinutes(5), 5, "http://localhost:8080",
                        "noreply@drss.example"
                ),
                "brevo",
                apiKey,
                client
        );
    }
}
