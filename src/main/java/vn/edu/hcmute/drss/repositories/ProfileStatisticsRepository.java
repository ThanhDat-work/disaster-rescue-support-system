package vn.edu.hcmute.drss.repositories;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ProfileStatisticsRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ProfileStatisticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Long> citizenStatuses(UUID userId) {
        return counts("""
                SELECT status AS category, COUNT(*) AS total
                FROM rescue_requests
                WHERE user_id = UNHEX(REPLACE(:userId, '-', ''))
                GROUP BY status
                """, params(userId, null));
    }

    public Map<String, Long> citizenMonths(UUID userId, LocalDateTime from) {
        return counts("""
                SELECT DATE_FORMAT(created_at, '%Y-%m') AS category,
                       COUNT(*) AS total
                FROM rescue_requests
                WHERE user_id = UNHEX(REPLACE(:userId, '-', ''))
                  AND created_at >= :fromDate
                GROUP BY DATE_FORMAT(created_at, '%Y-%m')
                ORDER BY category
                """, params(userId, from));
    }

    public Map<String, Long> coordinatorActions(UUID userId) {
        return counts("""
                SELECT new_status AS category, COUNT(*) AS total
                FROM status_histories
                WHERE changed_by_user_id = UNHEX(REPLACE(:userId, '-', ''))
                  AND actor_role = 'ROLE_COORDINATOR'
                  AND new_status IN ('VERIFIED', 'REJECTED', 'ASSIGNED', 'CLOSED')
                GROUP BY new_status
                """, params(userId, null));
    }

    public List<Map<String, Object>> coordinatorWeeks(UUID userId,
                                                        LocalDateTime from) {
        return jdbc.queryForList("""
                SELECT DATE_FORMAT(created_at, '%x-W%v') AS period,
                       new_status AS category, COUNT(*) AS total
                FROM status_histories
                WHERE changed_by_user_id = UNHEX(REPLACE(:userId, '-', ''))
                  AND actor_role = 'ROLE_COORDINATOR'
                  AND created_at >= :fromDate
                  AND new_status IN ('VERIFIED', 'REJECTED', 'ASSIGNED', 'CLOSED')
                GROUP BY period, new_status ORDER BY period, new_status
                """, params(userId, from));
    }

    public Map<String, Long> teamTaskStatuses(UUID userId) {
        return counts("""
                SELECT a.status AS category, COUNT(*) AS total
                FROM rescue_assignments a
                JOIN rescue_requests r ON r.id = a.request_id
                JOIN rescue_team_members m ON m.team_id = a.team_id
                WHERE m.user_id = UNHEX(REPLACE(:userId, '-', ''))
                  AND m.left_at IS NULL
                  AND r.user_id <> m.user_id
                GROUP BY a.status
                """, params(userId, null));
    }

    public Map<String, Long> teamCompletedWeeks(UUID userId,
                                                  LocalDateTime from) {
        return counts("""
                SELECT DATE_FORMAT(a.completion_time, '%x-W%v') AS category,
                       COUNT(*) AS total
                FROM rescue_assignments a
                JOIN rescue_requests r ON r.id = a.request_id
                JOIN rescue_team_members m ON m.team_id = a.team_id
                WHERE m.user_id = UNHEX(REPLACE(:userId, '-', ''))
                  AND m.left_at IS NULL
                  AND r.user_id <> m.user_id
                  AND a.status = 'COMPLETED'
                  AND a.completion_time >= :fromDate
                GROUP BY DATE_FORMAT(a.completion_time, '%x-W%v')
                ORDER BY category
                """, params(userId, from));
    }

    public long activeRoleCount(UUID userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_roles
                WHERE user_id = UNHEX(REPLACE(:userId, '-', ''))
                  AND revoked_at IS NULL
                """, params(userId, null), Long.class);
    }

    public long successfulSwitches(UUID userId, LocalDateTime from) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM role_switch_audits
                WHERE user_id = UNHEX(REPLACE(:userId, '-', ''))
                  AND outcome = 'SUCCESS' AND created_at >= :fromDate
                """, params(userId, from), Long.class);
    }

    public List<String> recentSwitchActivity(UUID userId) {
        return jdbc.query("""
                SELECT from_role, to_role, created_at
                FROM role_switch_audits
                WHERE user_id = UNHEX(REPLACE(:userId, '-', ''))
                  AND outcome = 'SUCCESS'
                ORDER BY created_at DESC, id DESC LIMIT 5
                """, params(userId, null), (rs, row) ->
                rs.getTimestamp("created_at").toLocalDateTime()
                        + " · " + (rs.getString("from_role") == null
                        ? "Đăng nhập ban đầu" : rs.getString("from_role"))
                        + " → " + rs.getString("to_role"));
    }

    private Map<String, Long> counts(String sql, MapSqlParameterSource parameters) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, parameters, rs -> {
            result.put(rs.getString("category"), rs.getLong("total"));
        });
        return result;
    }

    private MapSqlParameterSource params(UUID id, LocalDateTime from) {
        return new MapSqlParameterSource()
                .addValue("userId", id.toString())
                .addValue("fromDate", from);
    }
}