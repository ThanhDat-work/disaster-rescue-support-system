package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RoleSwitchAudit;

import java.util.UUID;

@Repository
public interface RoleSwitchAuditRepository
        extends JpaRepository<RoleSwitchAudit, UUID> {

    long countByUser_Id(UUID userId);
}