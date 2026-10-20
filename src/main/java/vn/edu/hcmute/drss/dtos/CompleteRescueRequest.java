package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompleteRescueRequest(

        @NotBlank(
                message = "Kết quả cứu hộ không được để trống."
        )
        @Size(
                max = 1000,
                message = "Kết quả cứu hộ không được vượt quá 1000 ký tự."
        )
        String notes
) {
}
