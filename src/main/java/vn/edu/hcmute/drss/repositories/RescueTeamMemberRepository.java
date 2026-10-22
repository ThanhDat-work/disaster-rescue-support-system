package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueTeamMember;
import vn.edu.hcmute.drss.entities.RescueTeamMemberId;
import vn.edu.hcmute.drss.entities.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RescueTeamMemberRepository
        extends JpaRepository<RescueTeamMember, RescueTeamMemberId> {

    @Query("""
            SELECT member
            FROM RescueTeamMember member
            JOIN FETCH member.user user
            JOIN FETCH member.team team
            LEFT JOIN FETCH team.leaderUser
            WHERE user.id = :userId
              AND team.id = :teamId
              AND member.leftAt IS NULL
              AND user.status = 'ACTIVE'
              AND EXISTS (
                  SELECT roleGrant.id
                  FROM UserRole roleGrant
                  WHERE roleGrant.user = user
                    AND roleGrant.role.roleName = 'ROLE_RESCUER'
                    AND roleGrant.revokedAt IS NULL
              )
            """)
    Optional<RescueTeamMember> findActiveMembership(
            @Param("userId") UUID userId,
            @Param("teamId") UUID teamId
    );

    boolean existsByTeam_IdAndLeftAtIsNull(UUID teamId);

    boolean existsByTeam_IdAndUser_IdAndLeftAtIsNull(
            UUID teamId,
            UUID userId
    );

    long countByUser_IdAndLeftAtIsNull(UUID userId);

    List<RescueTeamMember> findAllByTeam_Id(UUID teamId);

    @Query("""
            SELECT member
            FROM RescueTeamMember member
            JOIN FETCH member.user user
            JOIN FETCH member.team team
            LEFT JOIN FETCH team.leaderUser
            WHERE user.id = :userId
              AND member.leftAt IS NULL
              AND user.status = 'ACTIVE'
              AND EXISTS (
                  SELECT roleGrant.id
                  FROM UserRole roleGrant
                  WHERE roleGrant.user = user
                    AND roleGrant.role.roleName = 'ROLE_RESCUER'
                    AND roleGrant.revokedAt IS NULL
              )
            ORDER BY team.teamName ASC, team.displayCode ASC
            """)
    List<RescueTeamMember> findEligibleMembershipsByUserId(
            @Param("userId") UUID userId
    );

    @Query("""
            SELECT member
            FROM RescueTeamMember member
            JOIN FETCH member.user user
            WHERE member.team.id = :teamId
              AND member.leftAt IS NULL
              AND user.status = 'ACTIVE'
              AND EXISTS (
                  SELECT roleGrant.id
                  FROM UserRole roleGrant
                  WHERE roleGrant.user = user
                    AND roleGrant.role.roleName = 'ROLE_RESCUER'
                    AND roleGrant.revokedAt IS NULL
              )
            ORDER BY CASE WHEN member.role = 'LEADER' THEN 0 ELSE 1 END,
                     user.fullName ASC,
                     user.displayCode ASC
            """)
    List<RescueTeamMember> findEligibleMembersByTeamId(
            @Param("teamId") UUID teamId
    );

    @Query("""
            SELECT member.team.id AS teamId, COUNT(member) AS total
            FROM RescueTeamMember member
            JOIN member.user user
            WHERE member.team.id IN :teamIds
              AND member.leftAt IS NULL
              AND user.status = 'ACTIVE'
              AND EXISTS (
                  SELECT roleGrant.id
                  FROM UserRole roleGrant
                  WHERE roleGrant.user = user
                    AND roleGrant.role.roleName = 'ROLE_RESCUER'
                    AND roleGrant.revokedAt IS NULL
              )
            GROUP BY member.team.id
            """)
    List<TeamMemberCountProjection> countEligibleMembersByTeamIds(
            @Param("teamIds") Collection<UUID> teamIds
    );

    @Query("""
            SELECT COUNT(member)
            FROM RescueTeamMember member
            JOIN member.user user
            WHERE member.team.id = :teamId
              AND member.leftAt IS NULL
              AND user.status = 'ACTIVE'
              AND EXISTS (
                  SELECT roleGrant.id
                  FROM UserRole roleGrant
                  WHERE roleGrant.user = user
                    AND roleGrant.role.roleName = 'ROLE_RESCUER'
                    AND roleGrant.revokedAt IS NULL
              )
            """)
    long countEligibleMembersByTeamId(@Param("teamId") UUID teamId);

    @Query("""
            SELECT CASE WHEN COUNT(member) > 0 THEN TRUE ELSE FALSE END
            FROM RescueTeamMember member
            JOIN member.team team
            JOIN member.user user
            WHERE team.id = :teamId
              AND member.leftAt IS NULL
              AND user.status = 'ACTIVE'
              AND team.leaderUser IS NOT NULL
              AND EXISTS (
                  SELECT leaderMember.id
                  FROM RescueTeamMember leaderMember
                  WHERE leaderMember.team = team
                    AND leaderMember.user = team.leaderUser
                    AND leaderMember.role = 'LEADER'
                    AND leaderMember.leftAt IS NULL
                    AND team.leaderUser.status = 'ACTIVE'
                    AND EXISTS (
                        SELECT leaderGrant.id
                        FROM UserRole leaderGrant
                        WHERE leaderGrant.user = team.leaderUser
                          AND leaderGrant.role.roleName = 'ROLE_RESCUER'
                          AND leaderGrant.revokedAt IS NULL
                    )
              )
              AND EXISTS (
                  SELECT roleGrant.id
                  FROM UserRole roleGrant
                  WHERE roleGrant.user = user
                    AND roleGrant.role.roleName = 'ROLE_RESCUER'
                    AND roleGrant.revokedAt IS NULL
              )
            """)
    boolean existsEligibleRescuerByTeamId(@Param("teamId") UUID teamId);

    @Query("""
            SELECT DISTINCT member.user
            FROM RescueTeamMember member
            JOIN member.user user
            WHERE member.team.id = :teamId
              AND member.leftAt IS NULL
              AND user.status = 'ACTIVE'
              AND EXISTS (
                  SELECT roleGrant.id
                  FROM UserRole roleGrant
                  WHERE roleGrant.user = user
                    AND roleGrant.role.roleName = 'ROLE_RESCUER'
                    AND roleGrant.revokedAt IS NULL
              )
            ORDER BY user.id
            """)
    List<User> findActiveUsersByTeamId(@Param("teamId") UUID teamId);

    @Query("""
            SELECT COUNT(member)
            FROM RescueTeamMember member
            JOIN member.user user
            WHERE member.team.id = :teamId
              AND user.id = :userId
              AND member.leftAt IS NULL
              AND user.status = 'ACTIVE'
              AND EXISTS (
                  SELECT roleGrant.id
                  FROM UserRole roleGrant
                  WHERE roleGrant.user = user
                    AND roleGrant.role.roleName = 'ROLE_RESCUER'
                    AND roleGrant.revokedAt IS NULL
              )
            """)
    long countActiveMembershipForRealtime(
            @Param("userId") UUID userId,
            @Param("teamId") UUID teamId
    );
}