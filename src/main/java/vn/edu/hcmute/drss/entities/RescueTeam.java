package vn.edu.hcmute.drss.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "rescue_teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RescueTeam extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "display_code", nullable = false, unique = true, length = 20)
    private String displayCode;

    @Column(name = "team_name", nullable = false, unique = true, length = 100)
    private String teamName;

    @Column(name = "leader_name", nullable = false, length = 100)
    private String leaderName;

    @Column(name = "contact_number", nullable = false, length = 15)
    private String contactNumber;

    @Column(nullable = false, length = 32)
    private String status;
}