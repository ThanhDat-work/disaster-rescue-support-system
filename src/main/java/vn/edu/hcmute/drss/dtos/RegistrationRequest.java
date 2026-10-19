package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RegistrationRequest(
        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        @Pattern(regexp = "[0-9]{10,15}")
        String phoneNumber,

        @NotNull
        @Size(min = 12, max = 64)
        @Pattern(
                regexp = "(?s)(?=.*\\p{L})(?=.*\\d).+",
                message = "Password must contain a letter and a digit"
        )
        String password,

        @NotNull
        String confirmPassword
) {

    public RegistrationRequest {
        fullName = stripNullable(fullName);
        email = normalizeEmail(email);
        phoneNumber = stripNullable(phoneNumber);
    }

    @AssertTrue(message = "Password confirmation does not match")
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