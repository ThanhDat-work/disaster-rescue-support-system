package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetOtpRequest(
        @NotBlank(message = "Vui lòng nhập mã OTP.")
        @Pattern(
                regexp = "[0-9]{6}",
                message = "OTP phải gồm đúng 6 chữ số."
        )
        String otp
) {
    public ResetOtpRequest {
        otp = otp == null ? null : otp.strip();
    }
}
