package vn.edu.hcmute.drss.dtos;

import java.util.Objects;

public record StatisticsBreakdownItem(
        String code,
        String label,
        long count,
        int percentage,
        String tone) {

    public StatisticsBreakdownItem {
        code = requireText(code, "code");
        label = requireText(label, "label");
        tone = requireText(tone, "tone");

        if (count < 0) {
            throw new IllegalArgumentException(
                    "count must not be negative");
        }

        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException(
                    "percentage must be between 0 and 100");
        }
    }

    private static String requireText(
            String value,
            String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }

        return value;
    }
}