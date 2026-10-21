package vn.edu.hcmute.drss.dtos;

import java.util.List;
import java.util.UUID;

public record AdminUserSummary(
        UUID id,
        String displayCode,
        String fullName,
        String email,
        String phoneNumber,
        String status,
        List<String> roles) {
    public AdminUserSummary {
        roles = List.copyOf(roles);
    }
}