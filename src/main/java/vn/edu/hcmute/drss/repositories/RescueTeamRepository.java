package vn.edu.hcmute.drss.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueTeam;

import java.util.UUID;

@Repository
public interface RescueTeamRepository
        extends JpaRepository<RescueTeam, UUID> {

    @Query("""
            SELECT team
            FROM RescueTeam team
            WHERE (
                :keyword IS NULL
                OR LOWER(team.displayCode)
                    LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(team.teamName)
                    LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(team.leaderName)
                    LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            AND (
                :status IS NULL
                OR team.status = :status
            )
            """)
    Page<RescueTeam> searchAdminTeams(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );

    boolean existsByDisplayCodeIgnoreCase(String displayCode);

    boolean existsByDisplayCodeIgnoreCaseAndIdNot(
            String displayCode,
            UUID id
    );

    boolean existsByTeamNameIgnoreCase(String teamName);

    boolean existsByTeamNameIgnoreCaseAndIdNot(
            String teamName,
            UUID id
    );
}