package vn.edu.hcmute.drss.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rescue_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RescueRequest extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "display_code", nullable = false, unique = true, length = 20)
    private String displayCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disaster_type_id", nullable = false)
    private DisasterType disasterType;

    @Column(nullable = false, length = 32)
    private String urgency;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 500)
    private String address;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L; // Khóa lạc quan (Optimistic Lock)
}