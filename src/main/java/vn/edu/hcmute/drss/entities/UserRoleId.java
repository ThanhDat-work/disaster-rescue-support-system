package vn.edu.hcmute.drss.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserRoleId implements Serializable {
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "role_id")
    private Long roleId;
}