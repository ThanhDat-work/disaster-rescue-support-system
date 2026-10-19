package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.edu.hcmute.drss.entities.VerificationTokenPurpose;

import java.util.Locale;

public record ResendOtpRequest(
        @Email(message = "Email không hợp lệ.")
        @Size(max = 150, message = "Email quá dài.")
        String email,

        @NotNull(message = "Mục đích xác thực không hợp lệ.")
        VerificationTokenPurpose purpose
) {
    public ResendOtpRequest {
        email = email == null
                ? null
                : email.strip().toLowerCase(Locale.ROOT);
    }
}