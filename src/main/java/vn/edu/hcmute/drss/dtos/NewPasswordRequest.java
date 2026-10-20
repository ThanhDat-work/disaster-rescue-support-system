package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NewPasswordRequest(
        @NotNull(message = "Vui lòng nhập mật khẩu mới.")
        @Size(
                min = 12,
                max = 64,
                message = "Mật khẩu phải dài từ 12 đến 64 ký tự."
        )
        @Pattern(
                regexp = "(?s)(?=.*\\p{L})(?=.*\\d).+",
                message = "Mật khẩu phải có ít nhất một chữ cái và một chữ số."
        )
        String newPassword,

        @NotNull(message = "Vui lòng xác nhận mật khẩu.")
        String confirmPassword
) {
    @AssertTrue(message = "Mật khẩu xác nhận không khớp.")
    public boolean isPasswordConfirmed() {
        return newPassword != null
                && newPassword.equals(confirmPassword);
    }
}