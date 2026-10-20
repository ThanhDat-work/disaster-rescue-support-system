package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CancelCitizenRequest {
    @NotBlank(message = "Vui lòng nhập lý do hủy.")
    @Size(max = 2000, message = "Lý do hủy tối đa 2.000 ký tự.")
    private String reason;
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
}
