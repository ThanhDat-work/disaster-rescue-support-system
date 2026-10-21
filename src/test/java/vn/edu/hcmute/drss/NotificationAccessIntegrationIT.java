package vn.edu.hcmute.drss;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.Notification;
import vn.edu.hcmute.drss.entities.NotificationEventType;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.NotificationRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.security.IssuedAccessToken;
import vn.edu.hcmute.drss.security.JwtTokenService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
class NotificationAccessIntegrationIT {

    private static final UUID CITIZEN_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID COORDINATOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );
    private static final String COOKIE_NAME = "DRSS_ACCESS_TOKEN";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtTokenService jwtTokenService;
    @Autowired
    private EntityManager entityManager;

    private Notification citizenNotification;
    private Notification foreignNotification;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        notifications.flush();

        citizenNotification = notifications.saveAndFlush(
                notification(
                        users.findById(CITIZEN_ID).orElseThrow(),
                        "Thông báo của người dân"
                )
        );
        foreignNotification = notifications.saveAndFlush(
                notification(
                        users.findById(COORDINATOR_ID).orElseThrow(),
                        "Thông báo của điều phối viên"
                )
        );
    }

    @Test
    void reloadReturnsOnlyAuthenticatedUsersRows() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .cookie(citizenCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(content().string(containsString(
                        citizenNotification.getId().toString()
                )))
                .andExpect(content().string(not(containsString(
                        foreignNotification.getId().toString()
                ))));
    }

    @Test
    void ownerCanMarkReadAndRepeatedCallKeepsItRead()
            throws Exception {
        String endpoint = "/api/notifications/"
                + citizenNotification.getId()
                + "/read";

        mockMvc.perform(post(endpoint)
                        .cookie(citizenCookie())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        entityManager.flush();
        entityManager.clear();

        Notification firstRead = notifications.findById(
                citizenNotification.getId()
        ).orElseThrow();

        assertThat(firstRead.getIsRead()).isTrue();
        assertThat(firstRead.getReadAt()).isNotNull();

        var originalReadAt = firstRead.getReadAt();

        mockMvc.perform(post(endpoint)
                        .cookie(citizenCookie())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        entityManager.flush();
        entityManager.clear();

        assertThat(notifications.findById(
                citizenNotification.getId()
        ).orElseThrow().getReadAt()).isEqualTo(originalReadAt);
    }

    @Test
    void userCannotMarkAnotherUsersNotification() throws Exception {
        mockMvc.perform(post(
                        "/api/notifications/{notificationId}/read",
                        foreignNotification.getId()
                ).cookie(citizenCookie())
                        .with(csrf()))
                .andExpect(status().isNotFound());

        entityManager.flush();
        entityManager.clear();

        Notification unchanged = notifications.findById(
                foreignNotification.getId()
        ).orElseThrow();

        assertThat(unchanged.getIsRead()).isFalse();
        assertThat(unchanged.getReadAt()).isNull();
    }

    private Notification notification(
            User recipient,
            String title
    ) {
        Notification row = new Notification();
        row.setRecipientUser(recipient);
        row.setTitle(title);
        row.setMessage("Nội dung thông báo an toàn.");
        row.setEventType(NotificationEventType.REQUEST_VERIFIED);
        row.setReferenceType("RESCUE_REQUEST");
        row.setReferenceId(UUID.fromString(
                "66666666-6666-6666-6666-666666666666"
        ));
        row.setIsRead(false);
        return row;
    }

    private Cookie citizenCookie() {
        User citizen = users.findById(CITIZEN_ID).orElseThrow();
        IssuedAccessToken token = jwtTokenService.issue(
                citizen,
                List.of("ROLE_CITIZEN")
        );
        return new Cookie(COOKIE_NAME, token.tokenValue());
    }
}