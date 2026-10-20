package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.Size;

public record StartRescueRequest(

        @Size(
                max = 1000,
                message = "Ghi chú không được vượt quá 1000 ký tự."
        )
        String notes
) {
}
