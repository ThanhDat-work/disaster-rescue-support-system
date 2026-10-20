package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập email.")
        @Email(message = "Email không đúng định dạng.")
        @Size(
                max = 150,
                message = "Email không được vượt quá 150 ký tự."
        )
        String email,

        @NotBlank(message = "Vui lòng nhập mật khẩu.")
        @Size(
                max = 64,
                message = "Mật khẩu không được vượt quá 64 ký tự."
        )
        String password
) {

    public LoginRequest {
        email = email == null
                ? null
                : email.strip().toLowerCase(Locale.ROOT);
    }
}