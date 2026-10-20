package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueTeamMember;
import vn.edu.hcmute.drss.entities.RescueTeamMemberId;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RescueTeamMemberRepository
        extends JpaRepository<RescueTeamMember, RescueTeamMemberId> {

    @Query("""
            SELECT member
            FROM RescueTeamMember member
            JOIN FETCH member.user
            WHERE member.user.id = :userId
              AND member.team.id = :teamId
              AND member.leftAt IS NULL
            """)
    Optional<RescueTeamMember> findActiveMembership(
            @Param("userId") UUID userId,
            @Param("teamId") UUID teamId
    );
}