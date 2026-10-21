package vn.edu.hcmute.drss.entities;

public enum NotificationEventType {
    LEGACY,
    REQUEST_CREATED,
    REQUEST_VERIFIED,
    REQUEST_REJECTED,
    REQUEST_ASSIGNED,
    REQUEST_IN_PROGRESS,
    REQUEST_RESCUED,
    REQUEST_CANCELLED,
    REQUEST_CLOSED,
    REQUEST_STATUS_CHANGED,
    ASSIGNMENT_CREATED;

    public static NotificationEventType fromStatus(String status) {
        if (status == null) {
            return REQUEST_STATUS_CHANGED;
        }

        return switch (status) {
            case "VERIFIED" -> REQUEST_VERIFIED;
            case "REJECTED" -> REQUEST_REJECTED;
            case "ASSIGNED" -> REQUEST_ASSIGNED;
            case "IN_PROGRESS" -> REQUEST_IN_PROGRESS;
            case "RESCUED" -> REQUEST_RESCUED;
            case "CANCELLED" -> REQUEST_CANCELLED;
            case "CLOSED" -> REQUEST_CLOSED;
            default -> REQUEST_STATUS_CHANGED;
        };
    }
}