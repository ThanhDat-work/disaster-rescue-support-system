package vn.edu.hcmute.drss.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueTeam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RescueTeamRepository
        extends JpaRepository<RescueTeam, UUID> {

    long countByStatus(String status);

    @Query(value = """
            SELECT team
            FROM RescueTeam team
            LEFT JOIN FETCH team.leaderUser leader
            WHERE (
                :keyword IS NULL
                OR LOWER(team.displayCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(team.teamName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(team.leaderName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
              AND (:status IS NULL OR team.status = :status)
            """, countQuery = """
            SELECT COUNT(team)
            FROM RescueTeam team
            WHERE (
                :keyword IS NULL
                OR LOWER(team.displayCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(team.teamName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(team.leaderName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
              AND (:status IS NULL OR team.status = :status)
            """)
    Page<RescueTeam> searchAdminTeams(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );

    boolean existsByDisplayCodeIgnoreCase(String displayCode);
    boolean existsByDisplayCodeIgnoreCaseAndIdNot(String displayCode, UUID id);
    boolean existsByTeamNameIgnoreCase(String teamName);
    boolean existsByTeamNameIgnoreCaseAndIdNot(String teamName, UUID id);

    @Query("""
            SELECT team
            FROM RescueTeam team
            LEFT JOIN FETCH team.leaderUser
            WHERE team.id = :id
            """)
    Optional<RescueTeam> findByIdWithLeader(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT team
            FROM RescueTeam team
            LEFT JOIN FETCH team.leaderUser
            WHERE team.id = :id
            """)
    Optional<RescueTeam> findByIdForAdminUpdate(@Param("id") UUID id);

    @Query("""
            SELECT team
            FROM RescueTeam team
            JOIN team.leaderUser leader
            WHERE team.status = 'AVAILABLE'
              AND leader.status = 'ACTIVE'
              AND EXISTS (
                  SELECT leaderGrant.id
                  FROM UserRole leaderGrant
                  WHERE leaderGrant.user = leader
                    AND leaderGrant.role.roleName = 'ROLE_RESCUER'
                    AND leaderGrant.revokedAt IS NULL
              )
              AND EXISTS (
                  SELECT leaderMember.id
                  FROM RescueTeamMember leaderMember
                  WHERE leaderMember.team = team
                    AND leaderMember.user = leader
                    AND leaderMember.role = 'LEADER'
                    AND leaderMember.leftAt IS NULL
              )
              AND EXISTS (
                  SELECT member.id
                  FROM RescueTeamMember member
                  JOIN member.user memberUser
                  WHERE member.team = team
                    AND member.leftAt IS NULL
                    AND memberUser.status = 'ACTIVE'
                    AND EXISTS (
                        SELECT memberGrant.id
                        FROM UserRole memberGrant
                        WHERE memberGrant.user = memberUser
                          AND memberGrant.role.roleName = 'ROLE_RESCUER'
                          AND memberGrant.revokedAt IS NULL
                    )
              )
              AND NOT EXISTS (
                  SELECT ownerMember.id
                  FROM RescueTeamMember ownerMember
                  WHERE ownerMember.team = team
                    AND ownerMember.user.id = :requestOwnerId
                    AND ownerMember.leftAt IS NULL
              )
            ORDER BY team.teamName ASC, team.displayCode ASC
            """)
    List<RescueTeam> findAvailableWithActiveMembersForAssignmentExcludingOwner(
            @Param("requestOwnerId") UUID requestOwnerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT team
            FROM RescueTeam team
            LEFT JOIN FETCH team.leaderUser
            WHERE team.id = :id
            """)
    Optional<RescueTeam> findByIdForCoordinatorAssignment(
            @Param("id") UUID id
    );
}
