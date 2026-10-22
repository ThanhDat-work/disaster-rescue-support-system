package vn.edu.hcmute.drss.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDisplayCode(String displayCode);

    @Query(value = """
            SELECT u
            FROM User u
            WHERE (
                :keyword IS NULL
                OR LOWER(u.displayCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            AND (:status IS NULL OR u.status = :status)
            AND (
                :roleName IS NULL
                OR EXISTS (
                    SELECT ur.id
                    FROM UserRole ur
                    WHERE ur.user = u
                      AND ur.revokedAt IS NULL
                      AND ur.role.roleName = :roleName
                )
            )
            """, countQuery = """
            SELECT COUNT(u)
            FROM User u
            WHERE (
                :keyword IS NULL
                OR LOWER(u.displayCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            AND (:status IS NULL OR u.status = :status)
            AND (
                :roleName IS NULL
                OR EXISTS (
                    SELECT ur.id
                    FROM UserRole ur
                    WHERE ur.user = u
                      AND ur.revokedAt IS NULL
                      AND ur.role.roleName = :roleName
                )
            )
            """)
    Page<User> searchAdminUsers(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("roleName") String roleName,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT user
            FROM User user
            JOIN UserRole grantRow ON grantRow.user = user
            WHERE user.status = 'ACTIVE'
              AND grantRow.revokedAt IS NULL
              AND grantRow.role.roleName = 'ROLE_RESCUER'
            ORDER BY user.fullName ASC, user.displayCode ASC
            """)
    List<User> findEligibleRescuers();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT DISTINCT user
            FROM User user
            JOIN UserRole grantRow ON grantRow.user = user
            WHERE user.id IN :userIds
              AND user.status = 'ACTIVE'
              AND grantRow.revokedAt IS NULL
              AND grantRow.role.roleName = 'ROLE_RESCUER'
            ORDER BY user.id
            """)
    List<User> findEligibleRescuersByIdInForShare(
            @Param("userIds") Collection<UUID> userIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}