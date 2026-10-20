package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record ActivationRequest(
        @NotBlank(message = "Vui lòng nhập email.")
        @Email(message = "Email không hợp lệ.")
        @Size(max = 150, message = "Email quá dài.")
        String email,

        @NotBlank(message = "Vui lòng nhập mã OTP.")
        @Pattern(
                regexp = "[0-9]{6}",
                message = "OTP phải gồm đúng 6 chữ số."
        )
        String otp
) {
    public ActivationRequest {
        email = email == null
                ? null
                : email.strip().toLowerCase(Locale.ROOT);
        otp = otp == null ? null : otp.strip();
    }
}