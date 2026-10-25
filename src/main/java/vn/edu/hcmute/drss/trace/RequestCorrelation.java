package vn.edu.hcmute.drss.trace;

import org.slf4j.MDC;

import java.util.UUID;

public final class RequestCorrelation {

    public static final String KEY = "requestId";
    public static final String ATTRIBUTE = "requestCorrelationId";
    public static final String HEADER = "X-Request-ID";

    private RequestCorrelation() {
    }

    public static String currentId() {
        String id = MDC.get(KEY);
        if (id == null) return null;
        try {
            return UUID.fromString(id).toString().equals(id) ? id : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static String currentIdOrNew() {
        String current = currentId();
        return current == null ? UUID.randomUUID().toString() : current;
    }
}
