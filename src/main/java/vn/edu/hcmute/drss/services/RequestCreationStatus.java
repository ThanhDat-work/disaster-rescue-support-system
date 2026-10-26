package vn.edu.hcmute.drss.services;

import java.util.UUID;

public record RequestCreationStatus(String state, UUID requestId,
                                    boolean retryAllowed) {
}
