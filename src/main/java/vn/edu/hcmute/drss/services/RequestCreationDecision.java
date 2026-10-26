package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;
import java.util.UUID;

public record RequestCreationDecision(
        Kind kind, UUID requestId, LocalDateTime retryAt) {
    public enum Kind { NEW, REPLAY, PROCESSING, FAILED, UNKNOWN, LIMITED }

    public static RequestCreationDecision of(Kind kind) {
        return new RequestCreationDecision(kind, null, null);
    }
}
