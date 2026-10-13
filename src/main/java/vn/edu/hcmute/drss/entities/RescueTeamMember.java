package vn.edu.hcmute.drss.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rescue_team_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RescueTeamMember {
    @EmbeddedId
    private RescueTeamMemberId id;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;
}