package vn.edu.hcmute.drss.dtos;

import vn.edu.hcmute.drss.services.RequestStatistics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RequestStatisticsDashboard(
        LocalDate fromDate,
        LocalDate toDate,
        long totalRequests,
        List<StatisticsBreakdownItem> statusItems,
        List<StatisticsBreakdownItem> urgencyItems) {

    private static final Map<String, String> STATUS_LABELS = orderedMap(
            entry("PENDING", "Ch\u1EDD x\u00E1c minh"),
            entry("VERIFIED", "\u0110\u00E3 x\u00E1c minh"),
            entry("ASSIGNED", "\u0110\u00E3 ph\u00E2n c\u00F4ng"),
            entry("IN_PROGRESS", "\u0110ang c\u1EE9u h\u1ED9"),
            entry("RESCUED", "\u0110\u00E3 c\u1EE9u h\u1ED9"),
            entry("CLOSED", "\u0110\u00E3 \u0111\u00F3ng"),
            entry("REJECTED", "\u0110\u00E3 t\u1EEB ch\u1ED1i"),
            entry("CANCELLED", "\u0110\u00E3 h\u1EE7y"));

    private static final Map<String, String> STATUS_TONES = orderedMap(
            entry("PENDING", "bg-warning"),
            entry("VERIFIED", "bg-info"),
            entry("ASSIGNED", "bg-primary"),
            entry("IN_PROGRESS", "bg-primary"),
            entry("RESCUED", "bg-success"),
            entry("CLOSED", "bg-secondary"),
            entry("REJECTED", "bg-danger"),
            entry("CANCELLED", "bg-secondary"));

    private static final Map<String, String> URGENCY_LABELS = orderedMap(
            entry("LOW", "Th\u1EA5p"),
            entry("MEDIUM", "Trung b\u00ECnh"),
            entry("HIGH", "Cao"),
            entry("CRITICAL", "Kh\u1EA9n c\u1EA5p"));

    private static final Map<String, String> URGENCY_TONES = orderedMap(
            entry("LOW", "bg-success"),
            entry("MEDIUM", "bg-info"),
            entry("HIGH", "bg-warning"),
            entry("CRITICAL", "bg-danger"));

    public RequestStatisticsDashboard {
        Objects.requireNonNull(fromDate, "fromDate must not be null");
        Objects.requireNonNull(toDate, "toDate must not be null");
        Objects.requireNonNull(statusItems, "statusItems must not be null");
        Objects.requireNonNull(urgencyItems, "urgencyItems must not be null");

        if (totalRequests < 0) {
            throw new IllegalArgumentException(
                    "totalRequests must not be negative");
        }

        statusItems = List.copyOf(statusItems);
        urgencyItems = List.copyOf(urgencyItems);
    }

    public static RequestStatisticsDashboard from(
            RequestStatistics statistics) {
        Objects.requireNonNull(
                statistics,
                "statistics must not be null");

        return new RequestStatisticsDashboard(
                statistics.fromDate(),
                statistics.toDate(),
                statistics.totalRequests(),
                toItems(
                        statistics.countsByStatus(),
                        STATUS_LABELS,
                        STATUS_TONES,
                        statistics.totalRequests()),
                toItems(
                        statistics.countsByUrgency(),
                        URGENCY_LABELS,
                        URGENCY_TONES,
                        statistics.totalRequests()));
    }

    public boolean empty() {
        return totalRequests == 0L;
    }

    private static List<StatisticsBreakdownItem> toItems(
            Map<String, Long> counts,
            Map<String, String> labels,
            Map<String, String> tones,
            long total) {
        List<StatisticsBreakdownItem> items = new ArrayList<>();

        for (Map.Entry<String, String> label : labels.entrySet()) {
            String code = label.getKey();
            long count = counts.getOrDefault(code, 0L);

            int percentage = total == 0L
                    ? 0
                    : (int) Math.round(
                            count * 100.0d / total);

            items.add(new StatisticsBreakdownItem(
                    code,
                    label.getValue(),
                    count,
                    percentage,
                    tones.get(code)));
        }

        return List.copyOf(items);
    }

    @SafeVarargs
    private static Map<String, String> orderedMap(
            Map.Entry<String, String>... entries) {
        Map<String, String> values = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : entries) {
            values.put(entry.getKey(), entry.getValue());
        }

        return Collections.unmodifiableMap(values);
    }

    private static Map.Entry<String, String> entry(
            String key,
            String value) {
        return Map.entry(key, value);
    }
}