package vn.edu.hcmute.drss.services;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Kết quả thống kê đã được chuẩn hóa để controller và Thymeleaf chỉ đọc.
 */
public record RequestStatistics(
        LocalDate fromDate,
        LocalDate toDate,
        long totalRequests,
        Map<String, Long> countsByStatus,
        Map<String, Long> countsByUrgency
) {

    public RequestStatistics {
        Objects.requireNonNull(fromDate, "fromDate must not be null");
        Objects.requireNonNull(toDate, "toDate must not be null");
        Objects.requireNonNull(
                countsByStatus,
                "countsByStatus must not be null"
        );
        Objects.requireNonNull(
                countsByUrgency,
                "countsByUrgency must not be null"
        );

        if (totalRequests < 0) {
            throw new IllegalArgumentException(
                    "totalRequests must not be negative"
            );
        }

        countsByStatus = immutableOrderedCopy(countsByStatus);
        countsByUrgency = immutableOrderedCopy(countsByUrgency);
    }

    private static Map<String, Long> immutableOrderedCopy(
            Map<String, Long> source
    ) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(source)
        );
    }
}