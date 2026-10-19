package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetSessionServiceTest {

    private static final UUID TOKEN_ID = UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );

    private PasswordResetSessionService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetSessionService();
    }

    @Test
    void beginStoresOnlyNormalizedEmail() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        service.begin(request, "  Citizen@Example.COM  ");

        MockHttpSession session =
                (MockHttpSession) request.getSession(false);

        assertThat(session).isNotNull();
        assertThat(service.pendingEmail(session))
                .contains("citizen@example.com");
        assertThat(service.verifiedTokenId(session)).isEmpty();

        assertThat(Collections.list(session.getAttributeNames()))
                .hasSize(1);
    }

    @Test
    void establishGrantRotatesSessionAndRemovesEmail() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        service.begin(request, "citizen@example.com");

        String originalSessionId =
                request.getSession(false).getId();

        service.establishGrant(request, TOKEN_ID);

        MockHttpSession session =
                (MockHttpSession) request.getSession(false);

        assertThat(session.getId()).isNotEqualTo(originalSessionId);
        assertThat(service.pendingEmail(session)).isEmpty();
        assertThat(service.verifiedTokenId(session))
                .contains(TOKEN_ID);

        assertThat(Collections.list(session.getAttributeNames()))
                .hasSize(1);
    }

    @Test
    void anotherSessionCannotSeeEstablishedGrant() {
        MockHttpServletRequest firstRequest =
                new MockHttpServletRequest();

        service.begin(firstRequest, "citizen@example.com");
        service.establishGrant(firstRequest, TOKEN_ID);

        MockHttpSession otherSession = new MockHttpSession();

        assertThat(service.verifiedTokenId(otherSession)).isEmpty();
    }

    @Test
    void establishGrantRequiresStartedFlow() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        assertThatThrownBy(
                () -> service.establishGrant(request, TOKEN_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has not been started");
    }

    @Test
    void clearRemovesOnlyPasswordResetState() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        service.begin(request, "citizen@example.com");
        service.establishGrant(request, TOKEN_ID);

        MockHttpSession session =
                (MockHttpSession) request.getSession(false);

        session.setAttribute("unrelated", "keep-me");

        service.clear(session);

        assertThat(service.pendingEmail(session)).isEmpty();
        assertThat(service.verifiedTokenId(session)).isEmpty();
        assertThat(session.getAttribute("unrelated"))
                .isEqualTo("keep-me");
    }
}