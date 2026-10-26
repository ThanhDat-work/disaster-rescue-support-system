package vn.edu.hcmute.drss;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.Notification;
import vn.edu.hcmute.drss.entities.NotificationEventType;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.NotificationRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.security.JwtTokenService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
class NotificationBulkReadIntegrationIT {

    private static final UUID CITIZEN_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID COORDINATOR_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );

    @Autowired private MockMvc mvc;
    @Autowired private NotificationRepository notifications;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenService tokens;
    @Autowired private EntityManager entityManager;

    private UUID firstId;
    private UUID secondId;
    private UUID foreignId;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        notifications.flush();
        User citizen = users.findById(CITIZEN_ID).orElseThrow();
        User coordinator = users.findById(COORDINATOR_ID).orElseThrow();
        firstId = notifications.saveAndFlush(row(citizen, "Thứ nhất")).getId();
        secondId = notifications.saveAndFlush(row(citizen, "Thứ hai")).getId();
        foreignId = notifications.saveAndFlush(row(coordinator, "Người khác")).getId();
    }

    @Test
    void selectedReadOnlyUpdatesOwnedUnreadRowsAndIsIdempotent() throws Exception {
        String body = "[\"" + firstId + "\",\"" + foreignId
                + "\",\"" + firstId + "\"]";
        mvc.perform(post("/api/notifications/read-selected")
                        .cookie(citizenCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        entityManager.flush();
        entityManager.clear();
        LocalDateTime original = notifications.findById(firstId)
                .orElseThrow().getReadAt();
        assertThat(original).isNotNull();
        assertUnread(secondId);
        assertUnread(foreignId);

        mvc.perform(post("/api/notifications/read-selected")
                        .cookie(citizenCookie())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        entityManager.flush();
        entityManager.clear();
        assertThat(notifications.findById(firstId).orElseThrow().getReadAt())
                .isEqualTo(original);
        assertUnread(foreignId);
    }

    @Test
    void readAllOnlyUpdatesCurrentRecipientAndIsIdempotent() throws Exception {
        mvc.perform(post("/api/notifications/read-all")
                        .cookie(citizenCookie()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));

        entityManager.flush();
        entityManager.clear();
        LocalDateTime firstReadAt = notifications.findById(firstId)
                .orElseThrow().getReadAt();
        assertThat(firstReadAt).isNotNull();
        assertThat(notifications.findById(secondId).orElseThrow().getIsRead())
                .isTrue();
        assertUnread(foreignId);

        mvc.perform(post("/api/notifications/read-all")
                        .cookie(citizenCookie()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        entityManager.flush();
        entityManager.clear();
        assertThat(notifications.findById(firstId).orElseThrow().getReadAt())
                .isEqualTo(firstReadAt);
        assertUnread(foreignId);
    }

    @Test
    void bulkReadRequiresCsrf() throws Exception {
        mvc.perform(post("/api/notifications/read-all")
                        .cookie(citizenCookie()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/read-selected")
                        .cookie(citizenCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"" + firstId + "\"]"))
                .andExpect(status().isForbidden());
        assertUnread(firstId);
    }

    @Test
    void invalidOrExcessiveSelectionIsRejectedWithoutUpdate() throws Exception {
        String tooMany = IntStream.range(0, 51)
                .mapToObj(index -> "\"" + firstId + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        for (String body : List.of("null", "[null]", tooMany)) {
            mvc.perform(post("/api/notifications/read-selected")
                            .cookie(citizenCookie())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        assertUnread(firstId);
        assertUnread(foreignId);
    }

    private void assertUnread(UUID id) {
        Notification row = notifications.findById(id).orElseThrow();
        assertThat(row.getIsRead()).isFalse();
        assertThat(row.getReadAt()).isNull();
    }

    private Cookie citizenCookie() {
        User citizen = users.findById(CITIZEN_ID).orElseThrow();
        return new Cookie("DRSS_ACCESS_TOKEN", tokens.issue(
                citizen, List.of("ROLE_CITIZEN")
        ).tokenValue());
    }

    private Notification row(User recipient, String title) {
        Notification row = new Notification();
        row.setRecipientUser(recipient);
        row.setTitle(title);
        row.setMessage("Thông báo kiểm thử.");
        row.setEventType(NotificationEventType.REQUEST_VERIFIED);
        row.setReferenceType("RESCUE_REQUEST");
        row.setReferenceId(UUID.fromString(
                "66666666-6666-6666-6666-666666666666"
        ));
        row.setIsRead(false);
        return row;
    }
}
