package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.drss.entities.AdminUserAudit;
import vn.edu.hcmute.drss.entities.AdminUserAuditAction;

import java.util.List;
import java.util.UUID;

public interface AdminUserAuditRepository extends JpaRepository<AdminUserAudit, UUID> {

    List<AdminUserAudit> findByTargetUserIdAndActionOrderByCreatedAtDesc(
            UUID targetUserId, AdminUserAuditAction action);
}
