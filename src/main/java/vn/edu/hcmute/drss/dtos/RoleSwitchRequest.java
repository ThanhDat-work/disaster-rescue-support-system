package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleSwitchRequest(
        @NotBlank(message = "Vui lòng chọn vai trò.")
        @Pattern(
                regexp = "ROLE_(CITIZEN|COORDINATOR|RESCUER|ADMIN)",
                message = "Vai trò không hợp lệ."
        )
        String targetRole,

        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại.")
        @Size(
                max = 64,
                message = "Mật khẩu không được vượt quá 64 ký tự."
        )
        String password
) {
    public RoleSwitchRequest {
        targetRole = targetRole == null
                ? null
                : targetRole.strip();
    }
}