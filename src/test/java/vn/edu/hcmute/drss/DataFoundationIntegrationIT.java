package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DataFoundationIntegrationIT {

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testBCryptPasswordMatches() {
        String seededHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'admin@demo.com'", String.class);
        assertThat(passwordEncoder.matches("123456", seededHash)).isTrue();
    }

    @Test
    void testFlywayAndDatabaseSchema() {
        // 1. Lấy tên database động (Không ghi cứng drss_db)
        String currentSchema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

        // 2. Xác nhận chính xác V1 và V2 đều success
        List<String> successfulVersions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1", String.class);
        assertThat(successfulVersions).containsExactlyInAnyOrder("1", "2");

        // 3. Kiểm tra đủ 11 bảng nghiệp vụ + 1 bảng flyway
        List<String> actualTables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema = ?", String.class, currentSchema);
        assertThat(actualTables).containsExactlyInAnyOrder(
                "flyway_schema_history", "users", "roles", "user_roles", "disaster_types",
                "rescue_teams", "rescue_team_members", "rescue_requests", "rescue_assignments",
                "status_histories", "media_assets", "notifications"
        );

        // 4. Xác nhận tồn tại tài khoản coordinator
        Integer coordinatorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users u JOIN user_roles ur ON u.id = ur.user_id JOIN roles r ON ur.role_id = r.id WHERE r.role_name = 'ROLE_COORDINATOR'", Integer.class);
        assertThat(coordinatorCount).isGreaterThanOrEqualTo(1);

        // 5. Xác nhận REQ-0002 có ASN-0001
        Map<String, Object> assignmentData = jdbcTemplate.queryForMap(
                "SELECT a.display_code as asn_code, r.status as req_status FROM rescue_assignments a JOIN rescue_requests r ON a.request_id = r.id WHERE r.display_code = 'REQ-0002'");
        assertThat(assignmentData.get("asn_code")).isEqualTo("ASN-0001");
        assertThat(assignmentData.get("req_status")).isEqualTo("ASSIGNED");

       // 6. Xác nhận lịch sử do coordinator thực hiện
        List<Map<String, Object>> histories = jdbcTemplate.queryForList(
                "SELECT h.old_status, h.new_status, r_role.role_name " +
                "FROM status_histories h " +
                "JOIN rescue_requests req ON h.request_id = req.id " +
                "JOIN users u ON h.changed_by_user_id = u.id " +
                "JOIN user_roles ur ON u.id = ur.user_id " +
                "JOIN roles r_role ON ur.role_id = r_role.id " +
                "WHERE req.display_code = 'REQ-0002' " +
                "ORDER BY h.created_at ASC"
        );
        assertThat(histories).hasSize(2);
        
        assertThat(histories.get(0).get("old_status")).isEqualTo("PENDING");
        assertThat(histories.get(0).get("new_status")).isEqualTo("VERIFIED");
        assertThat(histories.get(0).get("role_name")).isEqualTo("ROLE_COORDINATOR"); // Xác nhận role người tạo lịch sử

        assertThat(histories.get(1).get("old_status")).isEqualTo("VERIFIED");
        assertThat(histories.get(1).get("new_status")).isEqualTo("ASSIGNED");
        assertThat(histories.get(1).get("role_name")).isEqualTo("ROLE_COORDINATOR"); // Xác nhận role người tạo lịch sử
    }
}