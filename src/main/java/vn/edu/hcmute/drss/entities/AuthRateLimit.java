package vn.edu.hcmute.drss.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "auth_rate_limits",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_rate_action_scope_subject",
                columnNames = {"action", "scope", "subject_key"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthRateLimit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuthRateLimitAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuthRateLimitScope scope;

    @Column(name = "subject_key", nullable = false, length = 64)
    private String subjectKey;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "penalty_level", nullable = false)
    private int penaltyLevel;

    @Column(name = "window_started_at", nullable = false)
    private LocalDateTime windowStartedAt;

    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    @Column(name = "last_attempt_at", nullable = false)
    private LocalDateTime lastAttemptAt;

    @Version
    @Column(nullable = false)
    private long version;
}