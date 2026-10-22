package vn.edu.hcmute.drss.dtos;

import java.util.List;
import java.util.UUID;

public record AdminUserDetail(
        UUID id,
        String displayCode,
        String fullName,
        String email,
        String phoneNumber,
        String status,
        String avatarSecureUrl,
        List<String> roles) {
    public AdminUserDetail {
        roles = List.copyOf(roles);
    }

    public AdminUserDetail(
            UUID id,
            String displayCode,
            String fullName,
            String email,
            String phoneNumber,
            String status,
            List<String> roles
    ) {
        this(id, displayCode, fullName, email, phoneNumber, status, null, roles);
    }
}