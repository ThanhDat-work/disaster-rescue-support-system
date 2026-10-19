package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RegistrationRequest(
        @NotBlank(message = "Vui lòng nhập họ và tên.")
        @Size(
                max = 100,
                message = "Họ và tên không được vượt quá 100 ký tự."
        )
        String fullName,

        @NotBlank(message = "Vui lòng nhập email.")
        @Email(message = "Email không đúng định dạng.")
        @Size(
                max = 150,
                message = "Email không được vượt quá 150 ký tự."
        )
        String email,

        @NotBlank(message = "Vui lòng nhập số điện thoại.")
        @Pattern(
                regexp = "[0-9]{10,15}",
                message = "Số điện thoại phải gồm từ 10 đến 15 chữ số."
        )
        String phoneNumber,

        @NotNull(message = "Vui lòng nhập mật khẩu.")
        @Size(
                min = 12,
                max = 64,
                message = "Mật khẩu phải dài từ 12 đến 64 ký tự."
        )
        @Pattern(
                regexp = "(?s)(?=.*\\p{L})(?=.*\\d).+",
                message = "Mật khẩu phải có ít nhất một chữ cái và một chữ số."
        )
        String password,

        @NotNull(message = "Vui lòng xác nhận mật khẩu.")
        String confirmPassword
) {

    public RegistrationRequest {
        fullName = stripNullable(fullName);
        email = normalizeEmail(email);
        phoneNumber = stripNullable(phoneNumber);
    }

    @AssertTrue(message = "Mật khẩu xác nhận không khớp.")
    public boolean isPasswordConfirmed() {
        return password != null
                && password.equals(confirmPassword);
    }

    private static String normalizeEmail(String value) {
        String stripped = stripNullable(value);

        return stripped == null
                ? null
                : stripped.toLowerCase(Locale.ROOT);
    }

    private static String stripNullable(String value) {
        return value == null ? null : value.strip();
    }
}