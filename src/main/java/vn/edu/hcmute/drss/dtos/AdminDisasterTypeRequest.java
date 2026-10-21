package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminDisasterTypeRequest(
        @NotBlank(message = "Mã loại thiên tai là bắt buộc")
        @Size(max = 20, message = "Mã loại thiên tai tối đa 20 ký tự")
        @Pattern(
                regexp = "[A-Za-z0-9-]+",
                message = "Mã chỉ gồm chữ, số và dấu gạch ngang"
        )
        String code,

        @NotBlank(message = "Tên loại thiên tai là bắt buộc")
        @Size(max = 100, message = "Tên loại thiên tai tối đa 100 ký tự")
        String name,

        String description
) {
}