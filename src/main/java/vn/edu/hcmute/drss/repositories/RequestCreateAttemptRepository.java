package vn.edu.hcmute.drss.repositories;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.drss.entities.RequestCreateAttempt;

public interface RequestCreateAttemptRepository
        extends JpaRepository<RequestCreateAttempt, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM RequestCreateAttempt a WHERE a.user.id = :userId AND a.operationKey = :key")
    Optional<RequestCreateAttempt> findOwnedForUpdate(
            @Param("userId") UUID userId, @Param("key") UUID key);

    Optional<RequestCreateAttempt> findByUser_IdAndOperationKey(
            UUID userId, UUID key);

    @Query("SELECT COUNT(a) FROM RequestCreateAttempt a WHERE a.user.id = :userId AND a.startedAt > :since")
    long countNewSince(@Param("userId") UUID userId,
                       @Param("since") LocalDateTime since);

    Optional<RequestCreateAttempt> findFirstByUser_IdAndStartedAtAfterOrderByStartedAtAsc(
            UUID userId, LocalDateTime since);
}
