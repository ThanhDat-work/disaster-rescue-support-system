package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InitialRoleSelectionSessionServiceTest {

    @Test
    void grantResolvesThenCanBeCleared() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-22T02:00:00Z"),
                ZoneOffset.UTC
        );
        InitialRoleSelectionSessionService service =
                new InitialRoleSelectionSessionService(clock);
        MockHttpServletRequest request =
                new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        UUID userId = UUID.randomUUID();

        service.begin(request, userId);

        assertThat(service.resolve(request.getSession(false)))
                .contains(userId);
        service.clear(request.getSession(false));
        assertThat(service.resolve(request.getSession(false)))
                .isEmpty();
    }
}