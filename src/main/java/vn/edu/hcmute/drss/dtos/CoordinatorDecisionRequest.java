package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CoordinatorDecisionRequest {

    @NotNull(message = "Vui lòng chọn hành động xử lý.")
    private CoordinatorDecisionAction action;

    @NotNull(message = "Thiếu phiên bản yêu cầu. Vui lòng tải lại trang.")
    private Long version;

    @Size(
            max = 2000,
            message = "Lý do từ chối tối đa 2.000 ký tự."
    )
    private String reason;

    public CoordinatorDecisionAction getAction() {
        return action;
    }

    public void setAction(CoordinatorDecisionAction action) {
        this.action = action;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}