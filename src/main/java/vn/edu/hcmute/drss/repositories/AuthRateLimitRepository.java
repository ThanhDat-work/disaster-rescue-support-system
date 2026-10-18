package vn.edu.hcmute.drss.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
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
}