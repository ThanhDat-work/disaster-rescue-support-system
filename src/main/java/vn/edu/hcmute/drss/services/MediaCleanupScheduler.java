package vn.edu.hcmute.drss.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.media",
        name = "cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MediaCleanupScheduler {

    private final MediaCleanupRetryService cleanup;
    private final int batchSize;

    public MediaCleanupScheduler(
            MediaCleanupRetryService cleanup,
            @Value("${app.media.cleanup-batch-size:25}") int batchSize
    ) {
        if (batchSize <= 0 || batchSize > 100) {
            throw new IllegalArgumentException(
                    "Media cleanup batch size must be between 1 and 100"
            );
        }
        this.cleanup = cleanup;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${app.media.cleanup-fixed-delay:PT5M}"
    )
    public void retryDueCleanup() {
        cleanup.retryDueTasks(batchSize);
    }
}