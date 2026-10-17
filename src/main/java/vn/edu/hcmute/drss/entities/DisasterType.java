package vn.edu.hcmute.drss.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "disaster_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DisasterType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disaster_code", nullable = false, unique = true, length = 20)
    private String disasterCode;

    @Column(name = "disaster_name", nullable = false, unique = true, length = 100)
    private String disasterName;

    @Column(columnDefinition = "TEXT")
    private String description;

   @Column(name = "is_active", nullable = false)
    private Boolean isActive = true; // Fix lỗi boolean số 6

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}