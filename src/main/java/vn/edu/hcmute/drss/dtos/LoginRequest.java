package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        @Size(max = 64)
        String password
) {

    public LoginRequest {
        email = email == null
                ? null
                : email.strip().toLowerCase(Locale.ROOT);
    }
}