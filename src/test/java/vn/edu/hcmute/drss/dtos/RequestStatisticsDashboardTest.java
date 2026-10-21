package vn.edu.hcmute.drss.dtos;

import org.junit.jupiter.api.Test;
import vn.edu.hcmute.drss.services.RequestStatistics;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestStatisticsDashboardTest {

    @Test
    void mapsLabelsCountsPercentagesAndPreservesAllCategories() {
        RequestStatistics statistics = new RequestStatistics(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 21),
                4L,
                statusCounts(3L, 1L),
                urgencyCounts(0L, 1L, 1L, 2L));

        RequestStatisticsDashboard dashboard = RequestStatisticsDashboard.from(statistics);

        assertThat(dashboard.empty()).isFalse();
        assertThat(dashboard.statusItems()).hasSize(8);
        assertThat(dashboard.urgencyItems()).hasSize(4);

        StatisticsBreakdownItem pending = dashboard.statusItems().get(0);

        assertThat(pending.code()).isEqualTo("PENDING");
        assertThat(pending.label()).isEqualTo("Ch\u1EDD x\u00E1c minh");
        assertThat(pending.count()).isEqualTo(3L);
        assertThat(pending.percentage()).isEqualTo(75);

        assertThat(dashboard.statusItems())
                .extracting(StatisticsBreakdownItem::code)
                .containsExactly(
                        "PENDING",
                        "VERIFIED",
                        "ASSIGNED",
                        "IN_PROGRESS",
                        "RESCUED",
                        "CLOSED",
                        "REJECTED",
                        "CANCELLED");
    }

    @Test
    void zeroTotalProducesEmptyDashboardAndZeroPercentages() {
        RequestStatistics statistics = new RequestStatistics(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 21),
                0L,
                statusCounts(0L, 0L),
                urgencyCounts(0L, 0L, 0L, 0L));

        RequestStatisticsDashboard dashboard = RequestStatisticsDashboard.from(statistics);

        assertThat(dashboard.empty()).isTrue();
        assertThat(dashboard.statusItems())
                .allMatch(item -> item.count() == 0L)
                .allMatch(item -> item.percentage() == 0);
        assertThat(dashboard.urgencyItems())
                .allMatch(item -> item.count() == 0L)
                .allMatch(item -> item.percentage() == 0);
    }

    private Map<String, Long> statusCounts(
            long pending,
            long verified) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("PENDING", pending);
        counts.put("VERIFIED", verified);
        counts.put("ASSIGNED", 0L);
        counts.put("IN_PROGRESS", 0L);
        counts.put("RESCUED", 0L);
        counts.put("CLOSED", 0L);
        counts.put("REJECTED", 0L);
        counts.put("CANCELLED", 0L);
        return counts;
    }

    private Map<String, Long> urgencyCounts(
            long low,
            long medium,
            long high,
            long critical) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("LOW", low);
        counts.put("MEDIUM", medium);
        counts.put("HIGH", high);
        counts.put("CRITICAL", critical);
        return counts;
    }
}