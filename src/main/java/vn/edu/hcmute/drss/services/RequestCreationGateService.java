package vn.edu.hcmute.drss.services;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.RequestCreateAttempt;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.RequestCreateAttemptRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;

@Service
public class RequestCreationGateService {
    private static final int MAX_FAILURE_RETRIES = 2;
    private final RequestCreateAttemptRepository attempts;
    private final UserRepository users;
    private final Clock clock;
    private final int maxPerMinute;

    public RequestCreationGateService(RequestCreateAttemptRepository attempts,
            UserRepository users, Clock clock,
            @Value("${app.request-create.max-per-minute:5}") int maxPerMinute) {
        if (maxPerMinute < 1) throw new IllegalArgumentException("Invalid creation quota");
        this.attempts = attempts;
        this.users = users;
        this.clock = clock;
        this.maxPerMinute = maxPerMinute;
    }

    // The controller calls the non-transactional workflow: this transaction
    // commits before media upload. Existing transactional IT fixtures join it.
    @Transactional
    public RequestCreationDecision claim(UUID userId, UUID key, String payloadHash) {
        if (userId == null || key == null || payloadHash == null
                || !payloadHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid request operation");
        }
        // Serialize all create attempts by one owner, including different keys.
        User owner = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!"ACTIVE".equals(owner.getStatus())) {
            throw new IllegalArgumentException("User not active");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        var previous = attempts.findOwnedForUpdate(userId, key);
        if (previous.isPresent()) {
            var existing = previous.orElseThrow();
            if (!payloadHash.equals(existing.getPayloadHash())) {
                throw new IllegalArgumentException("Mã thao tác đã được dùng cho nội dung khác.");
            }
            return switch (existing.getState()) {
                case SUCCEEDED -> new RequestCreationDecision(
                        RequestCreationDecision.Kind.REPLAY,
                        existing.getRequest().getId(), null);
                case FAILED -> {
                    if (existing.getRetryCount() >= MAX_FAILURE_RETRIES) {
                        yield RequestCreationDecision.of(RequestCreationDecision.Kind.FAILED);
                    }
                    // Same owner, key and exact payload; failure was persisted
                    // only after the prior request transaction rolled back.
                    existing.setRetryCount(existing.getRetryCount() + 1);
                    existing.setState(RequestCreateAttempt.State.PROCESSING);
                    existing.setStartedAt(now);
                    existing.setExpiresAt(now.plusMinutes(3));
                    attempts.saveAndFlush(existing);
                    yield RequestCreationDecision.of(RequestCreationDecision.Kind.NEW);
                }
                case UNKNOWN -> RequestCreationDecision.of(RequestCreationDecision.Kind.UNKNOWN);
                case PROCESSING -> {
                    if (now.isAfter(existing.getExpiresAt())) {
                        existing.setState(RequestCreateAttempt.State.UNKNOWN);
                        attempts.save(existing);
                        yield RequestCreationDecision.of(RequestCreationDecision.Kind.UNKNOWN);
                    }
                    yield RequestCreationDecision.of(RequestCreationDecision.Kind.PROCESSING);
                }
            };
        }
        if (attempts.countNewSince(userId, now.minusSeconds(60)) >= maxPerMinute) {
            LocalDateTime availableAt = attempts
                    .findFirstByUser_IdAndStartedAtAfterOrderByStartedAtAsc(
                            userId, now.minusSeconds(60))
                    .orElseThrow().getStartedAt().plusSeconds(60);
            return new RequestCreationDecision(RequestCreationDecision.Kind.LIMITED,
                    null, availableAt);
        }
        RequestCreateAttempt created = new RequestCreateAttempt();
        created.setUser(owner);
        created.setOperationKey(key);
        created.setPayloadHash(payloadHash);
        created.setState(RequestCreateAttempt.State.PROCESSING);
        created.setStartedAt(now);
        created.setExpiresAt(now.plusMinutes(3));
        attempts.saveAndFlush(created);
        return RequestCreationDecision.of(RequestCreationDecision.Kind.NEW);
    }

    // Called inside the SAME transaction as request, media and notifications.
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(UUID userId, UUID key, vn.edu.hcmute.drss.entities.RescueRequest request) {
        var attempt = attempts.findOwnedForUpdate(userId, key)
                .orElseThrow(() -> new IllegalStateException("Creation attempt missing"));
        if (attempt.getState() != RequestCreateAttempt.State.PROCESSING) {
            throw new IllegalStateException("Creation attempt is no longer active");
        }
        attempt.setRequest(request);
        attempt.setState(RequestCreateAttempt.State.SUCCEEDED);
        attempts.saveAndFlush(attempt);
    }

    @Transactional
    public void fail(UUID userId, UUID key) {
        attempts.findOwnedForUpdate(userId, key).ifPresent(attempt -> {
            if (attempt.getState() == RequestCreateAttempt.State.PROCESSING) {
                attempt.setState(RequestCreateAttempt.State.FAILED);
                attempts.saveAndFlush(attempt);
            }
        });
    }

    @Transactional(readOnly = true)
    public RequestCreationStatus status(UUID userId, UUID key) {
        if (userId == null || key == null) {
            throw new IllegalArgumentException("Invalid request operation");
        }
        return attempts.findByUser_IdAndOperationKey(userId, key)
                .map(attempt -> {
                    RequestCreateAttempt.State state = attempt.getState();
                    if (state == RequestCreateAttempt.State.PROCESSING
                            && LocalDateTime.now(clock).isAfter(attempt.getExpiresAt())) {
                        state = RequestCreateAttempt.State.UNKNOWN;
                    }
                    UUID requestId = state == RequestCreateAttempt.State.SUCCEEDED
                            ? attempt.getRequest().getId() : null;
                    return new RequestCreationStatus(state.name(), requestId,
                            state == RequestCreateAttempt.State.FAILED
                                    && attempt.getRetryCount() < MAX_FAILURE_RETRIES);
                })
                .orElse(new RequestCreationStatus("NOT_FOUND", null, true));
    }
}
