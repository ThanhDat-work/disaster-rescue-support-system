package vn.edu.hcmute.drss.services;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.repositories.RescueTeamMemberRepository;

@Service
public class RealtimeSubscriptionService {

    private final RescueTeamMemberRepository members;

    public RealtimeSubscriptionService(RescueTeamMemberRepository members) {
        this.members = members;
    }

    @Transactional(readOnly = true)
    public List<UUID> eligibleTeamIds(UUID userId, String activeRole) {
        Objects.requireNonNull(userId, "User ID is required");
        if (!"ROLE_RESCUER".equals(activeRole)) {
            return List.of();
        }
        return members.findEligibleMembershipsByUserId(userId).stream()
                .map(member -> member.getTeam().getId())
                .distinct()
                .toList();
    }
}
