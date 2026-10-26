package vn.edu.hcmute.drss;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.hcmute.drss.entities.AdminUserAuditAction;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.repositories.AdminUserAuditRepository;
import vn.edu.hcmute.drss.repositories.UserRepository;
import vn.edu.hcmute.drss.repositories.UserRoleRepository;
import vn.edu.hcmute.drss.services.AdminUserService;
import vn.edu.hcmute.drss.trace.RequestCorrelation;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("it")
class AdminUserAuditIntegrationIT {

    private static final UUID ADMIN_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111");

    @Autowired private AdminUserService service;
    @Autowired private AdminUserAuditRepository audits;
    @Autowired private UserRepository users;
    @Autowired private UserRoleRepository roles;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void auditCommitsWithMutationButRollbackAndRejectionLeaveNoSuccessRow() {
        User target = new User();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        target.setDisplayCode("AUD-" + suffix);
        target.setFullName("Audit test user");
        target.setEmail("audit-" + suffix + "@example.test");
        target.setPhoneNumber("0900000000");
        target.setPasswordHash("non-login-test-hash");
        target.setStatus("ACTIVE");
        target.setAuthVersion(0);
        target = users.saveAndFlush(target);
        UUID targetId = target.getId();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String requestId = UUID.randomUUID().toString();

        try {
            try (MDC.MDCCloseable ignored = MDC.putCloseable(
                    RequestCorrelation.KEY, requestId)) {
                tx.executeWithoutResult(status -> service.updateRoles(
                        targetId, Set.of("ROLE_CITIZEN"), ADMIN_ID));
            }
            var roleRows = audits.findByTargetUserIdAndActionOrderByCreatedAtDesc(
                    targetId, AdminUserAuditAction.ROLES_CHANGED);
            assertThat(roleRows).hasSize(1);
            assertThat(roleRows.get(0).getActorUserId()).isEqualTo(ADMIN_ID);
            assertThat(roleRows.get(0).getBeforeValue()).isEmpty();
            assertThat(roleRows.get(0).getAfterValue()).isEqualTo("ROLE_CITIZEN");
            assertThat(roleRows.get(0).getRequestId()).isEqualTo(requestId);

            tx.executeWithoutResult(status -> {
                service.updateRoles(targetId, Set.of("ROLE_COORDINATOR"), ADMIN_ID);
                assertThat(audits.findByTargetUserIdAndActionOrderByCreatedAtDesc(
                        targetId, AdminUserAuditAction.ROLES_CHANGED))
                        .hasSize(2);
                status.setRollbackOnly();
            });
            assertThat(audits.findByTargetUserIdAndActionOrderByCreatedAtDesc(
                    targetId, AdminUserAuditAction.ROLES_CHANGED)).hasSize(1);
            assertThat(users.findById(targetId).orElseThrow().getAuthVersion())
                    .isEqualTo(1);

            tx.executeWithoutResult(status -> {
                service.changeStatus(targetId, "BANNED", ADMIN_ID);
                assertThat(audits.findByTargetUserIdAndActionOrderByCreatedAtDesc(
                        targetId, AdminUserAuditAction.STATUS_CHANGED))
                        .hasSize(1);
                status.setRollbackOnly();
            });
            assertThat(users.findById(targetId).orElseThrow().getStatus())
                    .isEqualTo("ACTIVE");
            assertThat(audits.findByTargetUserIdAndActionOrderByCreatedAtDesc(
                    targetId, AdminUserAuditAction.STATUS_CHANGED)).isEmpty();

            assertThatThrownBy(() -> service.changeStatus(
                    targetId, "BANNED", targetId))
                    .isInstanceOf(AdminDomainException.class);
            assertThat(audits.findByTargetUserIdAndActionOrderByCreatedAtDesc(
                    targetId, AdminUserAuditAction.STATUS_CHANGED)).isEmpty();

            tx.executeWithoutResult(status -> service.changeStatus(
                    targetId, "BANNED", ADMIN_ID));
            var statusRows = audits.findByTargetUserIdAndActionOrderByCreatedAtDesc(
                    targetId, AdminUserAuditAction.STATUS_CHANGED);
            assertThat(statusRows).hasSize(1);
            assertThat(statusRows.get(0).getBeforeValue()).isEqualTo("ACTIVE");
            assertThat(statusRows.get(0).getAfterValue()).isEqualTo("BANNED");
        } finally {
            tx.executeWithoutResult(status -> {
                audits.deleteAll(audits.findByTargetUserIdAndActionOrderByCreatedAtDesc(
                        targetId, AdminUserAuditAction.STATUS_CHANGED));
                audits.deleteAll(audits.findByTargetUserIdAndActionOrderByCreatedAtDesc(
                        targetId, AdminUserAuditAction.ROLES_CHANGED));
                audits.flush();
                User persisted = users.findById(targetId).orElseThrow();
                roles.deleteAll(roles.findAllByUser(persisted));
                roles.flush();
                users.delete(persisted);
            });
        }
    }
}
