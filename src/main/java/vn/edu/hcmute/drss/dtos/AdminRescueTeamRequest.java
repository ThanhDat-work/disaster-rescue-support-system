package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminRescueTeamRequest(
        @NotBlank(message = "Mã đội là bắt buộc")
        @Size(max = 20, message = "Mã đội tối đa 20 ký tự")
        @Pattern(
                regexp = "[A-Za-z0-9-]+",
                message = "Mã đội chỉ gồm chữ, số và dấu gạch ngang"
        )
        String code,

        @NotBlank(message = "Tên đội là bắt buộc")
        @Size(max = 100, message = "Tên đội tối đa 100 ký tự")
        String name,

        @NotBlank(message = "Tên đội trưởng là bắt buộc")
        @Size(max = 100, message = "Tên đội trưởng tối đa 100 ký tự")
        String leaderName,

        @NotBlank(message = "Số điện thoại là bắt buộc")
        @Pattern(
                regexp = "[0-9]{10,15}",
                message = "Số điện thoại phải gồm 10 đến 15 chữ số"
        )
        String contactNumber,

        @NotBlank(message = "Trạng thái đội là bắt buộc")
        @Pattern(
                regexp = "AVAILABLE|BUSY|OFFLINE",
                message = "Trạng thái đội không hợp lệ"
        )
        String status
) {
}