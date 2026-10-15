package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueTeam;
import java.util.UUID;

@Repository
public interface RescueTeamRepository extends JpaRepository<RescueTeam, UUID> {
}