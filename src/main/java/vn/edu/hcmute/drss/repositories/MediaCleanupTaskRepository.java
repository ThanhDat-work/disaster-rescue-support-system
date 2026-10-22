package vn.edu.hcmute.drss.repositories;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.hcmute.drss.entities.MediaCleanupTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaCleanupTaskRepository
        extends JpaRepository<MediaCleanupTask, UUID> {

    Optional<MediaCleanupTask> findByPublicId(String publicId);

    List<MediaCleanupTask>
            findByCompletedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
                    LocalDateTime dueAt,
                    Pageable pageable
            );
}