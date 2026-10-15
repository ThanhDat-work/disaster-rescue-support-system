package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DataFoundationIntegrationTest {

    // Khởi tạo trực tiếp thay vì dùng @Autowired để tránh lỗi thiếu Bean
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testBCryptPasswordMatches() {
        // Lấy mã hash của tài khoản admin@demo.com từ CSDL để test khớp mật khẩu
        String seededHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'admin@demo.com'", String.class);
        
        assertThat(passwordEncoder.matches("123456", seededHash)).isTrue();
    }

    @Test
    void testFlywayAndDatabaseSchema() {
        // 1. Kiểm tra Flyway V1, V2 chạy thành công
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertThat(successfulMigrations).isGreaterThanOrEqualTo(2);

        // 2. Kiểm tra đủ 11 bảng (+1 bảng flyway)
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'drss_db'", Integer.class);
        assertThat(tableCount).isGreaterThanOrEqualTo(11);

        // 3. Kiểm tra Seed Data có tồn tại
        Long roleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Long.class);
        assertThat(roleCount).isGreaterThanOrEqualTo(4L);
    }
}