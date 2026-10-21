package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.entities.MediaCleanupTask;
import vn.edu.hcmute.drss.media.UploadedMedia;
import vn.edu.hcmute.drss.repositories.MediaCleanupTaskRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
public class MediaCleanupQueueService {

    private static final int MAX_FAILURE_CODE_LENGTH = 64;

    private final MediaCleanupTaskRepository tasks;

    public MediaCleanupQueueService(MediaCleanupTaskRepository tasks) {
        this.tasks = tasks;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(
            UploadedMedia media,
            String failureCode
    ) {
        Objects.requireNonNull(media, "Uploaded media is required");

        String publicId = requireText(media.publicId(), "Public ID");
        if (media.resourceType() == null) {
            throw new IllegalArgumentException(
                    "Media resource type is required"
            );
        }

        MediaCleanupTask task = tasks.findByPublicId(publicId)
                .orElseGet(MediaCleanupTask::new);

        task.setPublicId(publicId);
        task.setResourceType(media.resourceType());
        task.setFailureCode(normalizeFailureCode(failureCode));
        task.setAttemptCount(Math.max(1, task.getAttemptCount() + 1));
        task.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC));
        task.setCompletedAt(null);

        tasks.saveAndFlush(task);
    }

    private String normalizeFailureCode(String value) {
        String normalized = requireText(value, "Failure code")
                .toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9_:-]", "_");

        return normalized.length() <= MAX_FAILURE_CODE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_FAILURE_CODE_LENGTH);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }
        return value.trim();
    }
}