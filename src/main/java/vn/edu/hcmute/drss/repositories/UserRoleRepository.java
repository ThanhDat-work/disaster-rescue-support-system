package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.entities.UserRoleId;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}