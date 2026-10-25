package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.entities.RequestCreateAttempt;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.RequestCreateAttemptRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class RequestCreationGateServiceTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID KEY = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);
    @Mock private RequestCreateAttemptRepository attempts;
    @Mock private UserRepository users;
    private RequestCreationGateService gate;
    private User owner;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T02:00:00Z"), ZoneOffset.UTC);
        now = LocalDateTime.now(clock);
        gate = new RequestCreationGateService(attempts, users, clock, 5);
        owner = new User();
        owner.setId(OWNER);
        owner.setStatus("ACTIVE");
        when(users.findByIdForUpdate(OWNER)).thenReturn(Optional.of(owner));
    }

    @Test
    void firstAttemptClaimsKeyAndQuotaBeforeUpload() {
        when(attempts.findOwnedForUpdate(OWNER, KEY)).thenReturn(Optional.empty());
        when(attempts.countNewSince(eq(OWNER), any())).thenReturn(0L);
        var decision = gate.claim(OWNER, KEY, HASH);
        assertThat(decision.kind()).isEqualTo(RequestCreationDecision.Kind.NEW);
        verify(attempts).saveAndFlush(any(RequestCreateAttempt.class));
    }

    @Test
    void sameKeyStillProcessingDoesNotConsumeQuota() {
        var attempt = attempt(RequestCreateAttempt.State.PROCESSING);
        when(attempts.findOwnedForUpdate(OWNER, KEY)).thenReturn(Optional.of(attempt));
        assertThat(gate.claim(OWNER, KEY, HASH).kind())
                .isEqualTo(RequestCreationDecision.Kind.PROCESSING);
        verify(attempts, never()).countNewSince(eq(OWNER), any());
    }

    @Test
    void replayReturnsOriginalRequestAndRejectsChangedPayload() {
        var attempt = attempt(RequestCreateAttempt.State.SUCCEEDED);
        RescueRequest request = new RescueRequest();
        request.setId(UUID.randomUUID());
        attempt.setRequest(request);
        when(attempts.findOwnedForUpdate(OWNER, KEY)).thenReturn(Optional.of(attempt));
        assertThat(gate.claim(OWNER, KEY, HASH).requestId()).isEqualTo(request.getId());
        assertThatThrownBy(() -> gate.claim(OWNER, KEY, "b".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quotaIncludesNewOperationsButNotReplays() {
        when(attempts.findOwnedForUpdate(OWNER, KEY)).thenReturn(Optional.empty());
        when(attempts.countNewSince(eq(OWNER), any())).thenReturn(5L);
        var first = attempt(RequestCreateAttempt.State.FAILED);
        first.setStartedAt(now.minusSeconds(30));
        when(attempts.findFirstByUser_IdAndStartedAtAfterOrderByStartedAtAsc(eq(OWNER), any()))
                .thenReturn(Optional.of(first));
        var decision = gate.claim(OWNER, KEY, HASH);
        assertThat(decision.kind()).isEqualTo(RequestCreationDecision.Kind.LIMITED);
        assertThat(decision.retryAt()).isEqualTo(first.getStartedAt().plusSeconds(60));
        verify(attempts, never()).saveAndFlush(any());
    }

    @Test
    void expiredProcessingIsUnknownAndNeverTakenOver() {
        var attempt = attempt(RequestCreateAttempt.State.PROCESSING);
        attempt.setExpiresAt(now.minusSeconds(1));
        when(attempts.findOwnedForUpdate(OWNER, KEY)).thenReturn(Optional.of(attempt));
        assertThat(gate.claim(OWNER, KEY, HASH).kind())
                .isEqualTo(RequestCreationDecision.Kind.UNKNOWN);
        assertThat(attempt.getState()).isEqualTo(RequestCreateAttempt.State.UNKNOWN);
        verify(attempts, never()).countNewSince(eq(OWNER), any());
    }

    private RequestCreateAttempt attempt(RequestCreateAttempt.State state) {
        RequestCreateAttempt attempt = new RequestCreateAttempt();
        attempt.setUser(owner);
        attempt.setOperationKey(KEY);
        attempt.setPayloadHash(HASH);
        attempt.setState(state);
        attempt.setExpiresAt(now.plusSeconds(30));
        return attempt;
    }
}
