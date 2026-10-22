package vn.edu.hcmute.drss.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record ProfileView(
        String displayCode,
        String fullName,
        String email,
        String phoneNumber,
        String status,
        String activeRole,
        List<String> grantedRoles,
        LocalDateTime createdAt,
        String avatarUrl
) {
    public ProfileView {
        grantedRoles = List.copyOf(grantedRoles);
    }
}