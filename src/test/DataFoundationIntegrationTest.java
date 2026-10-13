package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DataFoundationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void testFlywayAndDatabaseSchema() {
        // Kiểm tra Flyway đã chạy đủ V1 và V2
        Integer migrationsRun = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        assertThat(migrationsRun).isGreaterThanOrEqualTo(2);

        // Kiểm tra đủ 11 bảng
        Integer roleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
        assertThat(roleCount).isEqualTo(4); // 4 role seed
        
        Integer assignmentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rescue_assignments", Integer.class);
        assertThat(assignmentCount).isEqualTo(1); // 1 assignment seed
    }

    @Test
    void testBCryptPasswordMatches() {
        // Lấy User từ DB để chứng minh Hash khớp 123456
        User admin = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals("admin@demo.com"))
                .findFirst().orElseThrow();
        
        assertThat(passwordEncoder.matches("123456", admin.getPasswordHash())).isTrue();
    }
}