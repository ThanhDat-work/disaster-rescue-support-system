package vn.edu.hcmute.drss;

import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.hcmute.drss.entities.AuthRateLimit;
import vn.edu.hcmute.drss.entities.VerificationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuthenticationPersistenceIntegrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationV3CreatesAuthenticationPersistenceSchema() {
        String currentSchema =
                jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

        List<String> successfulVersions = jdbcTemplate.queryForList(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success = 1
                """,
                String.class
        );

        assertThat(successfulVersions).contains("1", "2", "3");

        List<String> actualTables = jdbcTemplate.queryForList(
                """
                SELECT TABLE_NAME
                FROM information_schema.tables
                WHERE table_schema = ?
                """,
                String.class,
                currentSchema
        );

        assertThat(actualTables).contains(
                "users",
                "verification_tokens",
                "auth_rate_limits"
        );

        Integer authVersionColumnCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = 'users'
                  AND column_name = 'auth_version'
                  AND is_nullable = 'NO'
                """,
                Integer.class,
                currentSchema
        );

        assertThat(authVersionColumnCount).isEqualTo(1);

        Integer versionColumnCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name IN (
                      'verification_tokens',
                      'auth_rate_limits'
                  )
                  AND column_name = 'version'
                  AND is_nullable = 'NO'
                """,
                Integer.class,
                currentSchema
        );

        assertThat(versionColumnCount).isEqualTo(2);

        Integer seededUsersWithoutInitialAuthVersion = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users
                WHERE auth_version <> 0
                """,
                Integer.class
        );

        assertThat(seededUsersWithoutInitialAuthVersion).isZero();

        List<String> verificationTokenIndexes = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT index_name
                FROM information_schema.statistics
                WHERE table_schema = ?
                  AND table_name = 'verification_tokens'
                """,
                String.class,
                currentSchema
        );

        assertThat(verificationTokenIndexes).contains(
                "idx_verification_lookup",
                "idx_verification_cleanup"
        );

        List<String> rateLimitIndexes = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT index_name
                FROM information_schema.statistics
                WHERE table_schema = ?
                  AND table_name = 'auth_rate_limits'
                """,
                String.class,
                currentSchema
        );

        assertThat(rateLimitIndexes).contains(
                "uk_rate_action_scope_subject",
                "idx_rate_limit_blocked_until",
                "idx_rate_limit_last_attempt"
        );
    }

    @Test
    void mutableAuthenticationRecordsUseOptimisticLocking() throws Exception {
        assertThat(
                VerificationToken.class
                        .getDeclaredField("version")
                        .isAnnotationPresent(Version.class)
        ).isTrue();

        assertThat(
                AuthRateLimit.class
                        .getDeclaredField("version")
                        .isAnnotationPresent(Version.class)
        ).isTrue();
    }
}