package vn.edu.hcmute.drss.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class AdminRescueTeamRequest {

    @NotBlank(message = "Mã đội là bắt buộc")
    @Size(max = 20, message = "Mã đội tối đa 20 ký tự")
    @Pattern(
            regexp = "[A-Za-z0-9-]+",
            message = "Mã đội chỉ gồm chữ, số và dấu gạch ngang"
    )
    private String code;

    @NotBlank(message = "Tên đội là bắt buộc")
    @Size(max = 100, message = "Tên đội tối đa 100 ký tự")
    private String name;

    private String leaderName;

    @NotBlank(message = "Số điện thoại là bắt buộc")
    @Pattern(
            regexp = "[0-9]{10,15}",
            message = "Số điện thoại phải gồm 10 đến 15 chữ số"
    )
    private String contactNumber;

    @NotBlank(message = "Trạng thái đội là bắt buộc")
    @Pattern(
            regexp = "AVAILABLE|BUSY|OFFLINE",
            message = "Trạng thái đội không hợp lệ"
    )
    private String status;

    private UUID leaderUserId;
    private Set<UUID> memberUserIds = new LinkedHashSet<>();
    private final boolean legacyCompatibility;

    public AdminRescueTeamRequest() {
        this.legacyCompatibility = false;
    }

    public AdminRescueTeamRequest(
            String code,
            String name,
            String leaderName,
            String contactNumber,
            String status
    ) {
        this.code = code;
        this.name = name;
        this.leaderName = leaderName;
        this.contactNumber = contactNumber;
        this.status = status;
        this.legacyCompatibility = true;
    }

    public AdminRescueTeamRequest(
            String code,
            String name,
            String leaderName,
            String contactNumber,
            String status,
            UUID leaderUserId,
            Set<UUID> memberUserIds
    ) {
        this.code = code;
        this.name = name;
        this.leaderName = leaderName;
        this.contactNumber = contactNumber;
        this.status = status;
        this.leaderUserId = leaderUserId;
        setMemberUserIds(memberUserIds);
        this.legacyCompatibility = false;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getLeaderUserId() { return leaderUserId; }
    public void setLeaderUserId(UUID leaderUserId) { this.leaderUserId = leaderUserId; }
    public Set<UUID> getMemberUserIds() { return memberUserIds; }
    public void setMemberUserIds(Set<UUID> memberUserIds) {
        this.memberUserIds = memberUserIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(memberUserIds);
    }
    public boolean isLegacyCompatibility() { return legacyCompatibility; }
}