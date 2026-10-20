package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record ForgotPasswordRequest(
        @NotBlank(message = "Vui lòng nhập email.")
        @Email(message = "Email không hợp lệ.")
        @Size(max = 150, message = "Email quá dài.")
        String email
) {
    public ForgotPasswordRequest {
        email = email == null
                ? null
                : email.strip().toLowerCase(Locale.ROOT);
    }
}