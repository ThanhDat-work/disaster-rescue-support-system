package vn.edu.hcmute.drss;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.security.JwtTokenService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Transactional
class ProfileIntegrationIT {
    private static final UUID CITIZEN_ID = UUID.fromString(
            "33333333-3333-3333-3333-333333333333");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenService tokens;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void profileRendersForAuthenticatedCitizen() throws Exception {
        mockMvc.perform(get("/account/profile").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/account/profile"));
    }

    @Test
    void anonymousCannotReadAndPostRequiresCsrf() throws Exception {
        mockMvc.perform(get("/account/profile"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/account/profile")
                        .cookie(cookie())
                        .param("fullName", "New Name")
                        .param("phoneNumber", "0901234567"))
                .andExpect(status().isForbidden());
    }

    @Test
    void submittedEmailStatusRoleAndAvatarMetadataCannotBeMassAssigned()
            throws Exception {
        User before = users.findById(CITIZEN_ID).orElseThrow();
        String email = before.getEmail();
        String code = before.getDisplayCode();
        String avatarId = before.getAvatarPublicId();
        int version = before.getAuthVersion();
        mockMvc.perform(post("/account/profile").cookie(cookie())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("fullName", "  Profile Test  ")
                        .param("phoneNumber", "0901234567")
                        .param("email", "attacker@example.test")
                        .param("status", "BANNED")
                        .param("activeRole", "ROLE_ADMIN")
                        .param("avatarPublicId", "forged"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/account/profile"));
        users.flush();
        assertThat(before.getFullName()).isEqualTo("Profile Test");
        assertThat(before.getEmail()).isEqualTo(email);
        assertThat(before.getDisplayCode()).isEqualTo(code);
        assertThat(before.getStatus()).isEqualTo("ACTIVE");
        assertThat(before.getAuthVersion()).isEqualTo(version);
        assertThat(before.getAvatarPublicId()).isEqualTo(avatarId);
    }

    @Test
    void citizenStatisticsNeverIncludeOtherOwnersRequest() {
        Long expected = jdbc.queryForObject("""
                SELECT COUNT(*) FROM rescue_requests
                WHERE user_id = UNHEX(REPLACE(?, '-', ''))
                """, Long.class, CITIZEN_ID.toString());
        var report = new vn.edu.hcmute.drss.repositories.ProfileStatisticsRepository(
                new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        long total = report.citizenStatuses(CITIZEN_ID)
                .values().stream().mapToLong(Long::longValue).sum();
        assertThat(total).isEqualTo(expected);
    }

    private Cookie cookie() {
        User user = users.findById(CITIZEN_ID).orElseThrow();
        return new Cookie("DRSS_ACCESS_TOKEN",
                tokens.issue(user, "ROLE_CITIZEN").tokenValue());
    }
}