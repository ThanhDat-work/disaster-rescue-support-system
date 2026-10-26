package vn.edu.hcmute.drss.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_user_audits")
@Getter
@Setter
@NoArgsConstructor
public class AdminUserAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "actor_user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID actorUserId;

    @Column(name = "target_user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdminUserAuditAction action;

    @Column(name = "before_value", nullable = false, length = 255)
    private String beforeValue;

    @Column(name = "after_value", nullable = false, length = 255)
    private String afterValue;

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
