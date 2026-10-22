package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InitialRoleSelectionRequest(
        @NotBlank(message = "Vui lòng chọn vai trò.")
        @Pattern(
                regexp = "ROLE_(CITIZEN|COORDINATOR|RESCUER|ADMIN)",
                message = "Vai trò không hợp lệ."
        )
        String targetRole
) {
    public InitialRoleSelectionRequest {
        targetRole = targetRole == null
                ? null
                : targetRole.strip();
    }
}