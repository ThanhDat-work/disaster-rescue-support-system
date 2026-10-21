package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.entities.UserRoleId;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserRoleRepository
        extends JpaRepository<UserRole, UserRoleId> {

    @EntityGraph(attributePaths = "role")
    List<UserRole> findAllByUserAndRevokedAtIsNull(User user);

    @EntityGraph(attributePaths = "role")
    List<UserRole> findAllByUser(User user);

    @EntityGraph(attributePaths = { "user", "role" })
    List<UserRole> findAllByUserInAndRevokedAtIsNull(
            Collection<User> users);

    long countByRole_RoleNameAndRevokedAtIsNullAndUser_Status(
            String roleName,
            String status);
}