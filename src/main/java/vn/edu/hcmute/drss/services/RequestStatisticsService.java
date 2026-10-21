package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.repositories.RequestCountProjection;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class RequestStatisticsService {

    static final List<String> REQUEST_STATUSES = List.of(
            "PENDING",
            "VERIFIED",
            "ASSIGNED",
            "IN_PROGRESS",
            "RESCUED",
            "CLOSED",
            "REJECTED",
            "CANCELLED"
    );

    static final List<String> REQUEST_URGENCIES = List.of(
            "LOW",
            "MEDIUM",
            "HIGH",
            "CRITICAL"
    );

    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int MAXIMUM_RANGE_DAYS = 366;

    private final RescueRequestRepository rescueRequestRepository;

    public RequestStatisticsService(
            RescueRequestRepository rescueRequestRepository
    ) {
        this.rescueRequestRepository = rescueRequestRepository;
    }

    public RequestStatistics getStatistics(
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return getStatistics(fromDate, toDate, LocalDate.now());
    }

    RequestStatistics getStatistics(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate today
    ) {
        Objects.requireNonNull(today, "today must not be null");

        DateRange dateRange = resolveDateRange(
                fromDate,
                toDate,
                today
        );

        LocalDateTime fromInclusive =
                dateRange.fromDate().atStartOfDay();

        LocalDateTime toExclusive =
                dateRange.toDate().plusDays(1).atStartOfDay();

        List<RequestCountProjection> statusRows =
                rescueRequestRepository.countByStatusBetween(
                        fromInclusive,
                        toExclusive
                );

        List<RequestCountProjection> urgencyRows =
                rescueRequestRepository.countByUrgencyBetween(
                        fromInclusive,
                        toExclusive
                );

        Map<String, Long> countsByStatus = zeroCounts(
                REQUEST_STATUSES
        );

        Map<String, Long> countsByUrgency = zeroCounts(
                REQUEST_URGENCIES
        );

        long totalRequests = applyCounts(
                statusRows,
                countsByStatus
        );

        applyCounts(urgencyRows, countsByUrgency);

        return new RequestStatistics(
                dateRange.fromDate(),
                dateRange.toDate(),
                totalRequests,
countsByStatus,
                countsByUrgency
        );
    }

    private DateRange resolveDateRange(
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate today
    ) {
        if (fromDate == null && toDate == null) {
            return new DateRange(
                    today.minusDays(DEFAULT_RANGE_DAYS - 1L),
                    today
            );
        }

        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "Phải cung cấp đồng thời ngày bắt đầu và ngày kết thúc."
            );
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "Ngày bắt đầu không được sau ngày kết thúc."
            );
        }

        if (fromDate.isAfter(today) || toDate.isAfter(today)) {
            throw new IllegalArgumentException(
                    "Khoảng ngày thống kê không được nằm trong tương lai."
            );
        }

        long inclusiveDays = ChronoUnit.DAYS.between(
                fromDate,
                toDate
        ) + 1L;

        if (inclusiveDays > MAXIMUM_RANGE_DAYS) {
            throw new IllegalArgumentException(
                    "Khoảng ngày thống kê không được vượt quá 366 ngày."
            );
        }

        return new DateRange(fromDate, toDate);
    }

    private Map<String, Long> zeroCounts(
            List<String> categories
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();

        for (String category : categories) {
            counts.put(category, 0L);
        }

        return counts;
    }

    private long applyCounts(
            List<RequestCountProjection> rows,
            Map<String, Long> target
    ) {
        long total = 0L;

        for (RequestCountProjection row : rows) {
            if (row == null || row.getCategory() == null) {
                continue;
            }

            long count = row.getTotal();

            if (count < 0) {
                throw new IllegalStateException(
                        "Aggregate request count must not be negative"
                );
            }

            total += count;

            if (target.containsKey(row.getCategory())) {
                target.put(
                        row.getCategory(),
                        target.get(row.getCategory()) + count
                );
            }
        }

        return total;
    }

    private record DateRange(
            LocalDate fromDate,
            LocalDate toDate
    ) {
    }
}
