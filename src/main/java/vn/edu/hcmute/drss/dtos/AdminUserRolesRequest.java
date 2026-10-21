package vn.edu.hcmute.drss.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class AdminUserRolesRequest {

    @NotEmpty(message = "Người dùng phải có ít nhất một vai trò")
    @Valid
    private Set<@Pattern(regexp = "ROLE_(ADMIN|COORDINATOR|RESCUER|CITIZEN)", message = "Vai trò không hợp lệ") String> roles = new LinkedHashSet<>();

    public AdminUserRolesRequest(Set<String> roles) {
        if (roles != null) {
            this.roles = new LinkedHashSet<>(roles);
        }
    }
}