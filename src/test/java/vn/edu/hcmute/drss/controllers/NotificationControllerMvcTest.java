package vn.edu.hcmute.drss.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.hcmute.drss.dtos.NotificationInbox;
import vn.edu.hcmute.drss.dtos.NotificationItem;
import vn.edu.hcmute.drss.entities.NotificationEventType;
import vn.edu.hcmute.drss.exceptions.NotificationNotFoundException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.NotificationAccessService;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class NotificationControllerMvcTest {

    private static final UUID USER_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID NOTIFICATION_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationAccessService notificationAccess;

    @Test
    void anonymousReloadRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void authenticatedUserReloadsOnlyOwnInbox() throws Exception {
        NotificationInbox inbox = new NotificationInbox(
                List.of(item(false)),
                1L
        );
        when(notificationAccess.loadInbox(USER_ID))
                .thenReturn(inbox);

        mockMvc.perform(get("/api/notifications")
                        .with(authenticatedAs("ROLE_CITIZEN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].id")
                        .value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].secureUrl")
                        .doesNotExist())
                .andExpect(jsonPath("$.items[0].address")
                        .doesNotExist());

        verify(notificationAccess).loadInbox(USER_ID);
    }

    @Test
    void markReadRequiresCsrf() throws Exception {
        mockMvc.perform(post(
                        "/api/notifications/{notificationId}/read",
                        NOTIFICATION_ID
                ).with(authenticatedAs("ROLE_CITIZEN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void markReadUsesAuthenticatedUserId() throws Exception {
        when(notificationAccess.markRead(
                NOTIFICATION_ID,
                USER_ID
        )).thenReturn(item(true));

        mockMvc.perform(post(
                        "/api/notifications/{notificationId}/read",
                        NOTIFICATION_ID
                ).with(authenticatedAs("ROLE_RESCUER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.read").value(true));

        verify(notificationAccess).markRead(
                NOTIFICATION_ID,
                USER_ID
        );
    }

    @Test
    void foreignNotificationIsHiddenAsNotFound() throws Exception {
        when(notificationAccess.markRead(
                NOTIFICATION_ID,
                USER_ID
        )).thenThrow(new NotificationNotFoundException());

        mockMvc.perform(post(
                        "/api/notifications/{notificationId}/read",
                        NOTIFICATION_ID
                ).with(authenticatedAs("ROLE_CITIZEN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    private RequestPostProcessor authenticatedAs(String role) {
        AuthenticatedUserPrincipal principal =
                new AuthenticatedUserPrincipal(
                        USER_ID,
                        "notification-test@example.com",
                        "Notification Test User",
                        0,
                        List.of(role)
                );

        return authentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                )
        );
    }

    private NotificationItem item(boolean read) {
        return new NotificationItem(
                NOTIFICATION_ID,
                NotificationEventType.REQUEST_VERIFIED,
                "RESCUE_REQUEST",
                UUID.fromString(
                        "66666666-6666-6666-6666-666666666666"
                ),
                "Cập nhật yêu cầu",
                "Yêu cầu đã được xác minh.",
                read,
                LocalDateTime.of(2026, 10, 22, 8, 0),
                read
                        ? LocalDateTime.of(2026, 10, 22, 8, 5)
                        : null
        );
    }
}