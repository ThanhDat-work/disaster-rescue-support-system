package vn.edu.hcmute.drss.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "status_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "request_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID requestId;

    @Column(name = "old_status", nullable = false, length = 32)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(name = "changed_by_user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID changedByUserId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}