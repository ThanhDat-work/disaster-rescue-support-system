package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.repositories.AdminUserAuditRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.services.AdminUserService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("it")
class AdminUserAuditFailureIntegrationIT {

    private static final UUID ADMIN_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");

    @Autowired private AdminUserService service;
    @Autowired private UserRepository users;
    @MockitoBean private AdminUserAuditRepository audits;

    @Test
    void auditWriteFailureRollsBackStatusAndAuthVersion() {
        User target = new User();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        target.setDisplayCode("AUF-" + suffix);
        target.setFullName("Audit failure test user");
        target.setEmail("audit-failure-" + suffix + "@example.test");
        target.setPhoneNumber("0900000000");
        target.setPasswordHash("non-login-test-hash");
        target.setStatus("ACTIVE");
        target.setAuthVersion(0);
        UUID id = users.saveAndFlush(target).getId();
        try {
            when(audits.saveAndFlush(any())).thenThrow(
                    new DataIntegrityViolationException("test audit failure"));

            assertThatThrownBy(() -> service.changeStatus(
                    id, "BANNED", ADMIN_ID))
                    .isInstanceOf(DataIntegrityViolationException.class);

            User unchanged = users.findById(id).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo("ACTIVE");
            assertThat(unchanged.getAuthVersion()).isZero();
        } finally {
            users.deleteById(id);
        }
    }
}
