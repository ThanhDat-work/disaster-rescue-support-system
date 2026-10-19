package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.edu.hcmute.drss.dtos.LoginRequest;
import vn.edu.hcmute.drss.dtos.RegistrationRequest;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.services.AccountActivationService;
import vn.edu.hcmute.drss.services.AccountLoginService;
import vn.edu.hcmute.drss.services.AccountRegistrationService;
import vn.edu.hcmute.drss.services.ActivationResult;
import vn.edu.hcmute.drss.services.LoginResult;
import vn.edu.hcmute.drss.services.OtpResendResult;
import vn.edu.hcmute.drss.services.OtpResendService;
import vn.edu.hcmute.drss.services.PasswordResetCompletionResult;
import vn.edu.hcmute.drss.services.PasswordResetRequestResult;
import vn.edu.hcmute.drss.services.PasswordResetService;
import vn.edu.hcmute.drss.services.PasswordResetVerificationResult;
import vn.edu.hcmute.drss.services.RegistrationResult;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class AuthWebMvcTest {

    private static final String EMAIL = "citizen@example.com";
    private static final String IP = "203.0.113.30";
    private static final String GENERIC_REGISTRATION_MESSAGE =
            "Nếu thông tin hợp lệ, mã kích hoạt đã được gửi tới email của bạn.";
    private static final UUID TOKEN_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountRegistrationService registrationService;
    @MockitoBean
    private AccountActivationService activationService;
    @MockitoBean
    private AccountLoginService loginService;
    @MockitoBean
    private OtpResendService resendService;
    @MockitoBean
    private PasswordResetService resetService;

    @Test
    void publicAuthPagesRenderWithSharedLayout() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DRSS")));

        mockMvc.perform(get("/auth/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đăng ký công dân")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chào mừng trở lại")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(get("/auth/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đặt lại mật khẩu · Bước 1/3")));
    }

    @Test
    void registrationRequiresCsrf() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .param("fullName", "Citizen Test")
                        .param("email", EMAIL)
                        .param("phoneNumber", "0901234567")
                        .param("password", "secure password 123")
                        .param("confirmPassword", "secure password 123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRegistrationNeverCallsService() throws Exception {
        String password = "Do not echo password 987654";
        String confirmation = "Do not echo confirmation 123456";

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .param("fullName", "")
                        .param("email", "invalid")
                        .param("phoneNumber", "1")
                        .param("password", password)
                        .param("confirmPassword", confirmation))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("registrationRequest"))
                .andExpect(content().string(not(containsString(password))))
                .andExpect(content().string(not(containsString(confirmation))));

        verify(registrationService, never())
                .register(any(RegistrationRequest.class), anyString());
    }

    @Test
    void acceptedRegistrationRedirectsToActivation() throws Exception {
        when(registrationService.register(
                any(RegistrationRequest.class),
                anyString()
        )).thenReturn(RegistrationResult.accepted());

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(IP);
                            return request;
                        })
                        .param("fullName", "Citizen Test")
                        .param("email", EMAIL)
                        .param("phoneNumber", "0901234567")
                        .param("password", "secure password 123")
                        .param("confirmPassword", "secure password 123"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/verify-otp"))
                .andExpect(flash().attribute(
                        "successMsg",
                        GENERIC_REGISTRATION_MESSAGE
                ));
    }

    @Test
    void registrationMailFailureUsesSameGenericResponse()
            throws Exception {
        when(registrationService.register(
                any(RegistrationRequest.class),
                anyString()
        )).thenReturn(RegistrationResult.emailDeliveryFailed());

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(IP);
                            return request;
                        })
                        .param("fullName", "Citizen Test")
                        .param("email", EMAIL)
                        .param("phoneNumber", "0901234567")
                        .param("password", "secure password 123")
                        .param("confirmPassword", "secure password 123"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/verify-otp"))
                .andExpect(flash().attribute(
                        "successMsg",
                        GENERIC_REGISTRATION_MESSAGE
                ));
    }

    @Test
    void activationSuccessRedirectsToLogin() throws Exception {
        when(activationService.activate(
                EMAIL,
                "123456",
                IP
        )).thenReturn(ActivationResult.activated());

        mockMvc.perform(post("/auth/verify-otp")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(IP);
                            return request;
                        })
                        .param("email", EMAIL)
                        .param("otp", "123456"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login?activated"));
    }

    @Test
    void successfulLoginSetsHttpOnlyCookieAndRedirectsHome()
            throws Exception {
        when(loginService.login(
                any(LoginRequest.class),
                anyString()
        )).thenReturn(LoginResult.authenticated(
                new IssuedAccessToken(
                        "signed-jwt",
                        Instant.now().plusSeconds(1800)
                )
        ));

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .param("email", EMAIL)
                        .param("password", "secure password 123"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("DRSS_ACCESS_TOKEN=signed-jwt")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("HttpOnly")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("SameSite=Lax")
                ));
    }

    @Test
    void invalidLoginUsesGenericMessage() throws Exception {
        String submittedPassword = "Do not echo login password 123";

        when(loginService.login(
                any(LoginRequest.class),
                anyString()
        )).thenReturn(LoginResult.invalidCredentials());

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .param("email", EMAIL)
                        .param("password", submittedPassword))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Email hoặc mật khẩu không đúng."
                )))
                .andExpect(content().string(not(containsString(
                        submittedPassword
                ))));
    }

    @Test
    void forgotPasswordCreatesServerSideFlowState()
            throws Exception {
        when(resetService.requestReset(EMAIL, IP))
                .thenReturn(PasswordResetRequestResult.accepted());

        MvcResult result = mockMvc.perform(
                        post("/auth/forgot-password")
                                .with(csrf())
                                .with(request -> {
                                    request.setRemoteAddr(IP);
                                    return request;
                                })
                                .param("email", EMAIL)
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/reset-password/verify"
                ))
                .andReturn();

        MockHttpSession session = (MockHttpSession)
                result.getRequest().getSession(false);

        mockMvc.perform(get("/auth/reset-password/verify")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "c***@example.com"
                )))
                .andExpect(content().string(containsString(
                        "name=\"purpose\" value=\"PASSWORD_RESET\""
                )));
    }

    @Test
    void verifiedResetOtpCreatesGrantAndOpensPasswordPage()
            throws Exception {
        MockHttpSession session = startResetFlow();

        when(resetService.verifyResetOtp(
                EMAIL,
                "123456",
                IP
        )).thenReturn(
                PasswordResetVerificationResult.verified(TOKEN_ID)
        );

        MvcResult verification = mockMvc.perform(
                        post("/auth/reset-password/verify")
                                .session(session)
                                .with(csrf())
                                .with(request -> {
                                    request.setRemoteAddr(IP);
                                    return request;
                                })
                                .param("otp", "123456")
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/reset-password/new"
                ))
                .andReturn();

        MockHttpSession rotatedSession = (MockHttpSession)
                verification.getRequest().getSession(false);

        when(resetService.isGrantValid(TOKEN_ID))
                .thenReturn(true);

        mockMvc.perform(get("/auth/reset-password/new")
                        .session(rotatedSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Đặt lại mật khẩu · Bước 3/3"
                )));
    }

    @Test
    void resetResendUsesSessionEmailInsteadOfClientEmail()
            throws Exception {
        MockHttpSession session = startResetFlow();

        when(resendService.resend(
                EMAIL,
                VerificationTokenPurpose.PASSWORD_RESET,
                IP
        )).thenReturn(OtpResendResult.accepted());

        mockMvc.perform(post("/auth/resend-otp")
                        .session(session)
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(IP);
                            return request;
                        })
                        .param("email", "attacker@example.com")
                        .param("purpose", "PASSWORD_RESET"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/reset-password/verify"
                ))
                .andExpect(flash().attributeExists("successMsg"));

        verify(resendService).resend(
                EMAIL,
                VerificationTokenPurpose.PASSWORD_RESET,
                IP
        );
    }

    @Test
    void completedResetRedirectsToLoginAndClearsGrant()
            throws Exception {
        MockHttpSession session = verifiedResetSession();

        when(resetService.isGrantValid(TOKEN_ID))
                .thenReturn(true);
        when(resetService.completeReset(
                TOKEN_ID,
                "new secure password 123",
                "new secure password 123"
        )).thenReturn(PasswordResetCompletionResult.COMPLETED);

        mockMvc.perform(post("/auth/reset-password/new")
                        .session(session)
                        .with(csrf())
                        .param(
                                "newPassword",
                                "new secure password 123"
                        )
                        .param(
                                "confirmPassword",
                                "new secure password 123"
                        ))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login?reset"));

        when(resetService.isGrantValid(TOKEN_ID))
                .thenReturn(true);

        mockMvc.perform(get("/auth/reset-password/new")
                        .session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/forgot-password"
                ));
    }

    @Test
    void missingResetSessionCannotOpenNewPasswordPage()
            throws Exception {
        mockMvc.perform(get("/auth/reset-password/new"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "/auth/forgot-password"
                ))
                .andExpect(flash().attributeExists("errorMsg"));

        verify(resetService, never()).isGrantValid(any());
    }

    private MockHttpSession startResetFlow() throws Exception {
        when(resetService.requestReset(EMAIL, IP))
                .thenReturn(PasswordResetRequestResult.accepted());

        MvcResult result = mockMvc.perform(
                        post("/auth/forgot-password")
                                .with(csrf())
                                .with(request -> {
                                    request.setRemoteAddr(IP);
                                    return request;
                                })
                                .param("email", EMAIL)
                )
                .andReturn();

        return (MockHttpSession)
                result.getRequest().getSession(false);
    }

    private MockHttpSession verifiedResetSession()
            throws Exception {
        MockHttpSession session = startResetFlow();

        when(resetService.verifyResetOtp(
                EMAIL,
                "123456",
                IP
        )).thenReturn(
                PasswordResetVerificationResult.verified(TOKEN_ID)
        );

        MvcResult result = mockMvc.perform(
                        post("/auth/reset-password/verify")
                                .session(session)
                                .with(csrf())
                                .with(request -> {
                                    request.setRemoteAddr(IP);
                                    return request;
                                })
                                .param("otp", "123456")
                )
                .andReturn();

        return (MockHttpSession)
                result.getRequest().getSession(false);
    }
}