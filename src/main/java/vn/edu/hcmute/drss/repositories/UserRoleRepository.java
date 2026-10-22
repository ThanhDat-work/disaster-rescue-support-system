package vn.edu.hcmute.drss.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.entities.UserRole;
import vn.edu.hcmute.drss.entities.UserRoleId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserRoleRepository
        extends JpaRepository<UserRole, UserRoleId> {

    @EntityGraph(attributePaths = "role")
    List<UserRole> findAllByUserAndRevokedAtIsNull(User user);

    @EntityGraph(attributePaths = "role")
    List<UserRole> findAllByUser(User user);

    @EntityGraph(attributePaths = {"user", "role"})
    List<UserRole> findAllByUserInAndRevokedAtIsNull(Collection<User> users);

    long countByRole_RoleNameAndRevokedAtIsNullAndUser_Status(
            String roleName,
            String status
    );

    @Query("""
            SELECT DISTINCT userRole.user
            FROM UserRole userRole
            JOIN userRole.user user
            JOIN userRole.role role
            WHERE role.roleName = :roleName
              AND userRole.revokedAt IS NULL
              AND user.status = 'ACTIVE'
            ORDER BY user.id
            """)
    List<User> findActiveUsersByRoleName(@Param("roleName") String roleName);

    @Query("""
            SELECT COUNT(userRole)
            FROM UserRole userRole
            JOIN userRole.user user
            JOIN userRole.role role
            WHERE user.id = :userId
              AND role.roleName = :roleName
              AND userRole.revokedAt IS NULL
              AND user.status = 'ACTIVE'
            """)
    long countActiveRoleForRealtime(
            @Param("userId") UUID userId,
            @Param("roleName") String roleName
    );

    boolean existsByUserAndRole_RoleNameAndRevokedAtIsNull(
            User user,
            String roleName
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT userRole
            FROM UserRole userRole
            JOIN FETCH userRole.user user
            JOIN FETCH userRole.role role
            WHERE role.roleName = :roleName
              AND userRole.revokedAt IS NULL
              AND user.status = :status
            ORDER BY user.id
            """)
    List<UserRole> findActiveAssignmentsByRoleNameAndUserStatusForUpdate(
            @Param("roleName") String roleName,
            @Param("status") String status
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM rescue_team_members
            WHERE HEX(user_id) = REPLACE(UPPER(:userId), '-', '')
              AND left_at IS NULL
            """, nativeQuery = true)
    long countActiveTeamMemberships(@Param("userId") String userId);
}