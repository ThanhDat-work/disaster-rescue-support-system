package vn.edu.hcmute.drss.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueAssignment;

@Repository
public interface RescueAssignmentRepository
        extends JpaRepository<RescueAssignment, UUID> {

    @Query("""
            SELECT DISTINCT assignment
            FROM RescueAssignment assignment
            JOIN FETCH assignment.request request
            JOIN FETCH assignment.team team
            WHERE EXISTS (
                SELECT member.id
                FROM RescueTeamMember member
                WHERE member.team = team
                  AND member.user.id = :userId
                  AND member.leftAt IS NULL
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
              AND EXISTS (
                  SELECT member.id
                  FROM RescueTeamMember member
                  WHERE member.team = team
                    AND member.user.id = :userId
                    AND member.leftAt IS NULL
              )
            """)
    Optional<RescueAssignment> findByIdAndUserId(
            @Param("assignmentId") UUID assignmentId,
            @Param("userId") UUID userId
    );

    boolean existsByRequest_IdAndStatusIn(
            UUID requestId,
            Collection<String> statuses
    );

    @EntityGraph(attributePaths = {"request", "team"})
    Optional<RescueAssignment>
            findFirstByRequest_IdOrderByAssignedAtDescIdDesc(UUID requestId);

    @EntityGraph(attributePaths = {"request", "team"})
    List<RescueAssignment>
            findByRequest_IdInOrderByAssignedAtDescIdDesc(
                    Collection<UUID> requestIds
            );
}