package vn.edu.hcmute.drss.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueAssignment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RescueAssignmentRepository
        extends JpaRepository<RescueAssignment, UUID> {

    @Query("""
            SELECT DISTINCT assignment
            FROM RescueAssignment assignment
            JOIN FETCH assignment.request request
            JOIN FETCH assignment.team team
            WHERE request.user.id <> :userId
              AND EXISTS (
                  SELECT member.id
                  FROM RescueTeamMember member
                  JOIN member.user memberUser
                  WHERE member.team = team
                    AND memberUser.id = :userId
                    AND member.leftAt IS NULL
                    AND memberUser.status = 'ACTIVE'
                    AND EXISTS (
                        SELECT roleGrant.id
                        FROM UserRole roleGrant
                        WHERE roleGrant.user = memberUser
                          AND roleGrant.role.roleName = 'ROLE_RESCUER'
                          AND roleGrant.revokedAt IS NULL
                    )
              )
            ORDER BY assignment.assignedAt DESC
            """)
    List<RescueAssignment> findAssignmentsByUserId(
            @Param("userId") UUID userId
    );

    @Query("""
            SELECT DISTINCT assignment
            FROM RescueAssignment assignment
            JOIN FETCH assignment.request request
            JOIN FETCH assignment.team team
            WHERE assignment.id = :assignmentId
              AND request.user.id <> :userId
              AND EXISTS (
                  SELECT member.id
                  FROM RescueTeamMember member
                  JOIN member.user memberUser
                  WHERE member.team = team
                    AND memberUser.id = :userId
                    AND member.leftAt IS NULL
                    AND memberUser.status = 'ACTIVE'
                    AND EXISTS (
                        SELECT roleGrant.id
                        FROM UserRole roleGrant
                        WHERE roleGrant.user = memberUser
                          AND roleGrant.role.roleName = 'ROLE_RESCUER'
                          AND roleGrant.revokedAt IS NULL
                    )
              )
            """)
    Optional<RescueAssignment> findByIdAndUserId(
            @Param("assignmentId") UUID assignmentId,
            @Param("userId") UUID userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment
            FROM RescueAssignment assignment
            JOIN FETCH assignment.request request
            JOIN FETCH assignment.team team
            WHERE assignment.id = :assignmentId
              AND request.user.id <> :userId
              AND EXISTS (
                  SELECT member.id
                  FROM RescueTeamMember member
                  JOIN member.user memberUser
                  WHERE member.team = team
                    AND memberUser.id = :userId
                    AND member.leftAt IS NULL
                    AND memberUser.status = 'ACTIVE'
                    AND EXISTS (
                        SELECT roleGrant.id
                        FROM UserRole roleGrant
                        WHERE roleGrant.user = memberUser
                          AND roleGrant.role.roleName = 'ROLE_RESCUER'
                          AND roleGrant.revokedAt IS NULL
                    )
              )
            """)
    Optional<RescueAssignment> findByIdAndUserIdForUpdate(
            @Param("assignmentId") UUID assignmentId,
            @Param("userId") UUID userId
    );

    boolean existsByRequest_IdAndStatusIn(
            UUID requestId,
            Collection<String> statuses
    );

    @EntityGraph(attributePaths = {"request", "team"})
    Optional<RescueAssignment> findFirstByRequest_IdOrderByAssignedAtDescIdDesc(
            UUID requestId
    );

    @EntityGraph(attributePaths = {"request", "team"})
    List<RescueAssignment> findByRequest_IdInOrderByAssignedAtDescIdDesc(
            Collection<UUID> requestIds
    );
}