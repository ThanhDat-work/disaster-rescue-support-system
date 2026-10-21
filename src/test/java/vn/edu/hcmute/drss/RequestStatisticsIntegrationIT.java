package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.services.RequestStatistics;
import vn.edu.hcmute.drss.services.RequestStatisticsService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RequestStatisticsIntegrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RequestStatisticsService requestStatisticsService;

    @Test
    void migrationV4CreatesUrgencyTimeIndex() {
        String currentSchema = jdbcTemplate.queryForObject(
                "SELECT DATABASE()",
                String.class
        );

        List<String> successfulVersions = jdbcTemplate.queryForList(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success = 1
                """,
                String.class
        );

        assertThat(successfulVersions)
                .contains("1", "2", "3", "4");

        List<String> requestIndexes = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT index_name
                FROM information_schema.statistics
                WHERE table_schema = ?
                  AND table_name = 'rescue_requests'
                """,
                String.class,
                currentSchema
        );

        assertThat(requestIndexes).contains(
                "idx_req_status_time",
                "idx_req_urgency_time"
        );
    }

    @Test
    void aggregateQueriesUseInclusiveDatesAndExclusiveUpperBound() {
        LocalDate fromDate = LocalDate.of(2026, 1, 10);
        LocalDate toDate = LocalDate.of(2026, 1, 12);

        insertRequest(
                "LOW",
                "PENDING",
                fromDate.atStartOfDay()
        );

        insertRequest(
                "HIGH",
                "VERIFIED",
                LocalDateTime.of(2026, 1, 12, 23, 59, 59)
        );

        insertRequest(
                "CRITICAL",
                "CLOSED",
                fromDate.atStartOfDay().minusSeconds(1)
        );

        insertRequest(
                "CRITICAL",
                "ASSIGNED",
                toDate.plusDays(1).atStartOfDay()
        );

        RequestStatistics result =
                requestStatisticsService.getStatistics(
                        fromDate,
                        toDate
                );

        assertThat(result.totalRequests()).isEqualTo(2L);

        assertThat(result.countsByStatus())
                .containsEntry("PENDING", 1L)
                .containsEntry("VERIFIED", 1L)
.containsEntry("ASSIGNED", 0L)
                .containsEntry("CLOSED", 0L)
                .containsEntry("RESCUED", 0L);

        assertThat(result.countsByUrgency())
                .containsEntry("LOW", 1L)
                .containsEntry("HIGH", 1L)
                .containsEntry("MEDIUM", 0L)
                .containsEntry("CRITICAL", 0L);
    }

    private void insertRequest(
            String urgency,
            String status,
            LocalDateTime createdAt
    ) {
        String id = UUID.randomUUID().toString();

        String displayCode = "STAT-" + id
                .substring(0, 8)
                .toUpperCase();

        int insertedRows = jdbcTemplate.update(
                """
                INSERT INTO rescue_requests (
                    id,
                    display_code,
                    user_id,
                    disaster_type_id,
                    urgency,
                    status,
                    address,
                    description,
                    created_at,
                    updated_at,
                    version
                ) VALUES (
                    UNHEX(REPLACE(?, '-', '')),
                    ?,
                    UNHEX('33333333333333333333333333333333'),
                    (
                        SELECT id
                        FROM disaster_types
                        WHERE disaster_code = 'DIS-001'
                    ),
                    ?,
                    ?,
                    'Statistics integration test',
                    'Controlled statistics fixture',
                    ?,
                    ?,
                    0
                )
                """,
                id,
                displayCode,
                urgency,
                status,
                createdAt,
                createdAt
        );

        assertThat(insertedRows).isEqualTo(1);
    }
}
