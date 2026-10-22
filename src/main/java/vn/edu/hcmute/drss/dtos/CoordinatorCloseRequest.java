package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CoordinatorCloseRequest {

    @NotNull(message = "Phiên bản yêu cầu không hợp lệ.")
    private Long version;

    @NotBlank(message = "Vui lòng nhập ghi chú đóng yêu cầu.")
    @Size(
            min = 10,
            max = 500,
            message = "Ghi chú đóng phải có từ 10 đến 500 ký tự."
    )
    private String closeNote;
}