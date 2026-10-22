package vn.edu.hcmute.drss.dtos;

import java.util.List;
import java.util.UUID;

public record RescueTeamView(
        UUID id,
        String displayCode,
        String teamName,
        String contactNumber,
        String status,
        String currentUserRole,
        RescueTeamMemberView leader,
        long memberCount,
        List<RescueTeamMemberView> members
) {
    public RescueTeamView {
        members = members == null ? List.of() : List.copyOf(members);
    }
}