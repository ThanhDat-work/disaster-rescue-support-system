package vn.edu.hcmute.drss.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.MediaCleanupTask;
import vn.edu.hcmute.drss.media.MediaStorage;
import vn.edu.hcmute.drss.repositories.MediaCleanupTaskRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MediaCleanupRetryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            MediaCleanupRetryService.class
    );
    private static final int MAX_BATCH_SIZE = 100;
    private static final long MAX_BACKOFF_MINUTES = 60;

    private final MediaCleanupTaskRepository tasks;
    private final MediaStorage storage;
    private final Clock clock;

    public MediaCleanupRetryService(
            MediaCleanupTaskRepository tasks,
            MediaStorage storage,
            Clock clock
    ) {
        this.tasks = tasks;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public int retryDueTasks(int requestedBatchSize) {
        int batchSize = validateBatchSize(requestedBatchSize);
        LocalDateTime now = LocalDateTime.now(clock);

        List<MediaCleanupTask> due = tasks
                .findByCompletedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
                        now,
                        PageRequest.of(0, batchSize)
                );

        int completed = 0;
        for (MediaCleanupTask task : due) {
            int nextAttempt = task.getAttemptCount() + 1;
            task.setAttemptCount(nextAttempt);

            try {
                storage.delete(
                        task.getPublicId(),
                        task.getResourceType()
                );
                task.setCompletedAt(now);
                completed++;
            } catch (RuntimeException exception) {
                task.setFailureCode("RETRY_FAILED");
                task.setNextAttemptAt(
                        now.plusMinutes(backoffMinutes(nextAttempt))
                );
            }
        }

        if (!due.isEmpty()) {
            tasks.saveAllAndFlush(due);
        }

        if (completed > 0) {
            LOGGER.info(
                    "Completed {} queued media cleanup task(s)",
                    completed
            );
        }

        return completed;
    }

    private int validateBatchSize(int value) {
        if (value <= 0 || value > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Cleanup batch size must be between 1 and "
                            + MAX_BATCH_SIZE
            );
        }
        return value;
    }

    private long backoffMinutes(int attemptCount) {
        return Math.min(
                MAX_BACKOFF_MINUTES,
                Math.max(1L, attemptCount * 5L)
        );
    }
}