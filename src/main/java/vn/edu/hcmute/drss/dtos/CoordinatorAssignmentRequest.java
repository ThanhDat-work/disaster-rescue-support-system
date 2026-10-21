package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class CoordinatorAssignmentRequest {

    @NotNull(message = "Vui lòng chọn đội cứu hộ.")
    private UUID teamId;

    @NotNull(message = "Thiếu phiên bản yêu cầu. Vui lòng tải lại trang.")
    private Long version;

    @Size(
            max = 2000,
            message = "Ghi chú phân công tối đa 2.000 ký tự."
    )
    private String coordinatorNote;

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getCoordinatorNote() {
        return coordinatorNote;
    }

    public void setCoordinatorNote(String coordinatorNote) {
        this.coordinatorNote = coordinatorNote;
    }
}