package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
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
import vn.edu.hcmute.drss.repositories.RequestCreateAttemptRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class RequestCreationStatusTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID KEY = UUID.randomUUID();
    @Mock private RequestCreateAttemptRepository attempts;
    @Mock private UserRepository users;
    private RequestCreationGateService gate;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T03:00:00Z"), ZoneOffset.UTC);
        now = LocalDateTime.now(clock);
        gate = new RequestCreationGateService(attempts, users, clock, 5);
    }

    @Test
    void unknownKeyDoesNotRevealOtherOwnersOperation() {
        assertThat(gate.status(OWNER, KEY).state()).isEqualTo("NOT_FOUND");
        verifyNoInteractions(users);
    }

    @Test
    void processingPastDeadlineReportsUnknownWithoutMutatingRow() {
        var row = row(RequestCreateAttempt.State.PROCESSING);
        row.setExpiresAt(now.minusSeconds(1));
        when(attempts.findByUser_IdAndOperationKey(OWNER, KEY))
                .thenReturn(Optional.of(row));
        assertThat(gate.status(OWNER, KEY).state()).isEqualTo("UNKNOWN");
        assertThat(row.getState()).isEqualTo(RequestCreateAttempt.State.PROCESSING);
    }

    @Test
    void successReturnsCommittedIdAndFailureAllowsOnlyBoundedRetry() {
        var row = row(RequestCreateAttempt.State.SUCCEEDED);
        var request = new RescueRequest();
        request.setId(UUID.randomUUID());
        row.setRequest(request);
        when(attempts.findByUser_IdAndOperationKey(OWNER, KEY))
                .thenReturn(Optional.of(row));
        assertThat(gate.status(OWNER, KEY).requestId()).isEqualTo(request.getId());
        row.setState(RequestCreateAttempt.State.FAILED);
        assertThat(gate.status(OWNER, KEY).retryAllowed()).isTrue();
        row.setRetryCount(2);
        assertThat(gate.status(OWNER, KEY).retryAllowed()).isFalse();
    }

    private RequestCreateAttempt row(RequestCreateAttempt.State state) {
        var row = new RequestCreateAttempt();
        row.setState(state);
        row.setExpiresAt(now.plusMinutes(2));
        return row;
    }
}
