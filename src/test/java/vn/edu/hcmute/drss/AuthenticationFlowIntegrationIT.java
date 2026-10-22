package vn.edu.hcmute.drss;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.entities.VerificationToken;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.repositories.VerificationTokenRepository;
import vn.edu.hcmute.drss.services.GmailOtpEmailService;
import vn.edu.hcmute.drss.exceptions.OtpMailDeliveryException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(
        AuthenticationFlowIntegrationIT
                .ProtectedRoutesConfiguration.class
)
class AuthenticationFlowIntegrationIT {

    private static final String FLOW_EMAIL =
            "t201-flow-integration@example.com";
    private static final String ORIGINAL_PASSWORD =
            "Original password 123";
    private static final String NEW_PASSWORD =
            "Replacement password 456";
    private static final String FLOW_IP = "203.0.113.81";
    private static final String SECOND_IP = "203.0.113.82";
    private static final String COOKIE_NAME =
            "DRSS_ACCESS_TOKEN";
    private static final String GENERIC_REGISTRATION_MESSAGE =
            "Nếu thông tin hợp lệ, mã kích hoạt đã được gửi tới email của bạn.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private VerificationTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private GmailOtpEmailService emailService;

    @BeforeEach
    void setUp() {
        cleanIntegrationData();
        reset(emailService);
    }

    @AfterEach
    void tearDown() {
        cleanIntegrationData();
    }

    @Test
    void fullCitizenFlowPersistsSecurityStateAndInvalidatesOldJwt()
            throws Exception {
        String activationOtp = registerAndCaptureActivationOtp();

        User inactiveUser = userRepository
                .findByEmailIgnoreCase(FLOW_EMAIL)
                .orElseThrow();

        int initialAuthVersion = inactiveUser.getAuthVersion();

        assertThat(inactiveUser.getStatus()).isEqualTo("INACTIVE");
        assertThat(inactiveUser.getPasswordHash())
                .isNotEqualTo(ORIGINAL_PASSWORD);
        assertThat(passwordEncoder.matches(
                ORIGINAL_PASSWORD,
                inactiveUser.getPasswordHash()
        )).isTrue();

        List<UserRole> citizenRoles = userRoleRepository
                .findAllByUserAndRevokedAtIsNull(inactiveUser);

        assertThat(citizenRoles)
                .extracting(userRole ->
                        userRole.getRole().getRoleName()
                )
                .containsExactly("ROLE_CITIZEN");

        VerificationToken activationToken = tokenRepository
                .findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        inactiveUser,
                        VerificationTokenPurpose.ACCOUNT_ACTIVATION
                )
                .orElseThrow();

        assertThat(activationToken.getTokenDigest())
                .hasSize(64)
                .isNotEqualTo(activationOtp);

        mockMvc.perform(post("/auth/verify-otp")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(FLOW_IP);
                            return request;
                        })
                        .param("email", FLOW_EMAIL)
                        .param("otp", activationOtp))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/login?activated"
                ));

        User activeUser = userRepository
                .findByEmailIgnoreCase(FLOW_EMAIL)
                .orElseThrow();

        assertThat(activeUser.getStatus()).isEqualTo("ACTIVE");
        assertThat(tokenRepository.findById(
                activationToken.getId()
        ).orElseThrow().getConsumedAt()).isNotNull();

        Cookie oldAccessToken = loginAndExtractCookie(
                ORIGINAL_PASSWORD,
                FLOW_IP
        );

        mockMvc.perform(get("/requests/integration")
                        .cookie(oldAccessToken))
                .andExpect(status().isOk())
                .andExpect(content().string("citizen"));

        clearInvocations(emailService);

        MvcResult resetRequest = mockMvc.perform(
                        post("/auth/forgot-password")
                                .with(csrf())
                                .with(request -> {
                                    request.setRemoteAddr(FLOW_IP);
                                    return request;
                                })
                                .param("email", FLOW_EMAIL)
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/reset-password/verify"
                ))
                .andExpect(flash().attributeExists("successMsg"))
                .andReturn();

        MockHttpSession resetSession = (MockHttpSession)
                resetRequest.getRequest().getSession(false);

        String resetOtp = captureOtp(
                VerificationTokenPurpose.PASSWORD_RESET
        );

        VerificationToken resetToken = tokenRepository
                .findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        activeUser,
                        VerificationTokenPurpose.PASSWORD_RESET
                )
                .orElseThrow();

        String sessionIdBeforeVerification =
                resetSession.getId();

        MvcResult verification = mockMvc.perform(
                        post("/auth/reset-password/verify")
                                .session(resetSession)
                                .with(csrf())
                                .with(request -> {
                                    request.setRemoteAddr(FLOW_IP);
                                    return request;
                                })
                                .param("otp", resetOtp)
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/reset-password/new"
                ))
                .andReturn();

        MockHttpSession verifiedSession = (MockHttpSession)
                verification.getRequest().getSession(false);

        assertThat(verifiedSession.getId())
                .isNotEqualTo(sessionIdBeforeVerification);

        mockMvc.perform(post("/auth/reset-password/new")
                        .session(verifiedSession)
                        .with(csrf())
                        .param("newPassword", NEW_PASSWORD)
                        .param("confirmPassword", NEW_PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login?reset"))
                .andExpect(header().doesNotExist(
                        HttpHeaders.SET_COOKIE
                ));

        User passwordResetUser = userRepository
                .findByEmailIgnoreCase(FLOW_EMAIL)
                .orElseThrow();

        assertThat(passwordEncoder.matches(
                NEW_PASSWORD,
                passwordResetUser.getPasswordHash()
        )).isTrue();
        assertThat(passwordResetUser.getAuthVersion())
                .isEqualTo(initialAuthVersion + 1);
        assertThat(tokenRepository.findById(
                resetToken.getId()
        ).orElseThrow().getConsumedAt()).isNotNull();

        mockMvc.perform(get("/requests/integration")
                        .cookie(oldAccessToken))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));

        Cookie currentAccessToken = loginAndExtractCookie(
                NEW_PASSWORD,
                SECOND_IP
        );

        mockMvc.perform(get("/requests/integration")
                        .cookie(currentAccessToken))
                .andExpect(status().isOk());

        UserRole citizenRole = userRoleRepository
                .findAllByUserAndRevokedAtIsNull(
                        passwordResetUser
                )
                .get(0);

        citizenRole.setRevokedAt(LocalDateTime.now());
        userRoleRepository.saveAndFlush(citizenRole);

        mockMvc.perform(get("/requests/integration")
                        .cookie(currentAccessToken))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));

        citizenRole.setRevokedAt(null);
        userRoleRepository.saveAndFlush(citizenRole);

        passwordResetUser.setStatus("BANNED");
        userRepository.saveAndFlush(passwordResetUser);

        mockMvc.perform(get("/requests/integration")
                        .cookie(currentAccessToken))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void publicAuthFailuresKeepGenericResponsesAndRequireCsrf()
            throws Exception {
        mockMvc.perform(post("/auth/login")
                        .param("email", "missing@example.com")
                        .param("password", "Wrong password 123"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(FLOW_IP);
                            return request;
                        })
                        .param("email", "missing@example.com")
                        .param("password", "Wrong password 123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Email hoặc mật khẩu không đúng."
                )));

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(SECOND_IP);
                            return request;
                        })
                        .param("email", "dan@demo.com")
                        .param("password", "Wrong password 123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Email hoặc mật khẩu không đúng."
                )));

        clearInvocations(emailService);

        mockMvc.perform(post("/auth/forgot-password")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(FLOW_IP);
                            return request;
                        })
                        .param(
                                "email",
                                "unknown-reset@example.com"
                        ))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/reset-password/verify"
                ))
                .andExpect(flash().attribute(
                        "successMsg",
                        "Nếu email hợp lệ, mã đặt lại mật khẩu đã được gửi."
                ));

        verifyNoInteractions(emailService);
    }

    @Test
    void registrationMailFailureKeepsPersistenceAndGenericResponse()
            throws Exception {
        doThrow(new OtpMailDeliveryException(
                new IllegalStateException("test-only mail failure")
        )).when(emailService).sendOtp(
                eq(FLOW_EMAIL),
                eq(VerificationTokenPurpose.ACCOUNT_ACTIVATION),
                anyString()
        );

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(FLOW_IP);
                            return request;
                        })
                        .param("fullName", "T2-01 Mail Failure Citizen")
                        .param("email", FLOW_EMAIL)
                        .param("phoneNumber", "0901234567")
                        .param("password", ORIGINAL_PASSWORD)
                        .param("confirmPassword", ORIGINAL_PASSWORD))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/verify-otp"))
                .andExpect(flash().attribute(
                        "successMsg",
                        GENERIC_REGISTRATION_MESSAGE
                ));

        User user = userRepository
                .findByEmailIgnoreCase(FLOW_EMAIL)
                .orElseThrow();

        assertThat(user.getStatus()).isEqualTo("INACTIVE");
        assertThat(passwordEncoder.matches(
                ORIGINAL_PASSWORD,
                user.getPasswordHash()
        )).isTrue();

        assertThat(userRoleRepository
                .findAllByUserAndRevokedAtIsNull(user))
                .extracting(userRole ->
                        userRole.getRole().getRoleName()
                )
                .containsExactly("ROLE_CITIZEN");

        VerificationToken token = tokenRepository
                .findFirstByUserAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        user,
                        VerificationTokenPurpose.ACCOUNT_ACTIVATION
                )
                .orElseThrow();

        assertThat(token.getTokenDigest()).hasSize(64);
        assertThat(token.getConsumedAt()).isNull();
    }

    private String registerAndCaptureActivationOtp()
            throws Exception {
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(FLOW_IP);
                            return request;
                        })
                        .param("fullName", "T2-01 Integration Citizen")
                        .param("email", FLOW_EMAIL)
                        .param("phoneNumber", "0901234567")
                        .param("password", ORIGINAL_PASSWORD)
                        .param(
                                "confirmPassword",
                                ORIGINAL_PASSWORD
                        ))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/verify-otp"));

        return captureOtp(
                VerificationTokenPurpose.ACCOUNT_ACTIVATION
        );
    }

    private String captureOtp(
            VerificationTokenPurpose purpose
    ) {
        ArgumentCaptor<String> otpCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(emailService).sendOtp(
                eq(FLOW_EMAIL),
                eq(purpose),
                otpCaptor.capture()
        );

        String otp = otpCaptor.getValue();
        assertThat(otp).matches("[0-9]{6}");
        return otp;
    }

    private Cookie loginAndExtractCookie(
            String password,
            String ipAddress
    ) throws Exception {
        MvcResult login = mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(ipAddress);
                            return request;
                        })
                        .param("email", FLOW_EMAIL)
                        .param("password", password))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/requests"))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("HttpOnly")
                ))
                .andReturn();

        String setCookie = login.getResponse().getHeader(
                HttpHeaders.SET_COOKIE
        );

        assertThat(setCookie)
                .isNotBlank()
                .startsWith(COOKIE_NAME + "=");

        int valueStart = (COOKIE_NAME + "=").length();
        int valueEnd = setCookie.indexOf(';', valueStart);

        assertThat(valueEnd).isGreaterThan(valueStart);

        return new Cookie(
                COOKIE_NAME,
                setCookie.substring(valueStart, valueEnd)
        );
    }

    private void cleanIntegrationData() {
        jdbcTemplate.update("DELETE FROM auth_rate_limits");

        jdbcTemplate.update(
                """
                DELETE FROM verification_tokens
                WHERE user_id = (
                    SELECT id
                    FROM users
                    WHERE email = ?
                )
                """,
                FLOW_EMAIL
        );

        jdbcTemplate.update(
                """
                DELETE FROM user_roles
                WHERE user_id = (
                    SELECT id
                    FROM users
                    WHERE email = ?
                )
                """,
                FLOW_EMAIL
        );

        jdbcTemplate.update(
                "DELETE FROM users WHERE email = ?",
                FLOW_EMAIL
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProtectedRoutesConfiguration {

        @Bean
        ProtectedRoutesController protectedRoutesController() {
            return new ProtectedRoutesController();
        }
    }

    @RestController
    static class ProtectedRoutesController {

        @GetMapping("/requests/integration")
        String citizenRoute() {
            return "citizen";
        }
    }
}