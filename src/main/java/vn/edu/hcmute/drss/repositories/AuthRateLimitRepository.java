package vn.edu.hcmute.drss.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.AuthRateLimit;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.AuthRateLimitScope;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthRateLimitRepository
        extends JpaRepository<AuthRateLimit, UUID> {

    Optional<AuthRateLimit> findByActionAndScopeAndSubjectKey(
            AuthRateLimitAction action,
            AuthRateLimitScope scope,
            String subjectKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rateLimit
            FROM AuthRateLimit rateLimit
            WHERE rateLimit.action = :action
              AND rateLimit.scope = :scope
              AND rateLimit.subjectKey = :subjectKey
            """)
    Optional<AuthRateLimit> findForUpdate(
            @Param("action") AuthRateLimitAction action,
            @Param("scope") AuthRateLimitScope scope,
            @Param("subjectKey") String subjectKey
    );

    long deleteByActionAndScopeAndSubjectKey(
            AuthRateLimitAction action,
            AuthRateLimitScope scope,
            String subjectKey
    );
}