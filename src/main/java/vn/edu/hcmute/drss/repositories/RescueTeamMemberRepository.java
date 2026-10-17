package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.RescueTeamMember;
import vn.edu.hcmute.drss.entities.RescueTeamMemberId;

@Repository
public interface RescueTeamMemberRepository extends JpaRepository<RescueTeamMember, RescueTeamMemberId> {
}