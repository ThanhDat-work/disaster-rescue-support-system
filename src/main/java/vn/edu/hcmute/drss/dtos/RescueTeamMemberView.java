package vn.edu.hcmute.drss.dtos;

import java.util.UUID;

public record RescueTeamMemberView(
        UUID userId,
        String displayCode,
        String fullName,
        String avatarSecureUrl,
        String teamRole
) {
}