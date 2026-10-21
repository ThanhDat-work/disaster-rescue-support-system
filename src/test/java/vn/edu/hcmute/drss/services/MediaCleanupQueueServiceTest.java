package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.entities.MediaCleanupTask;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.media.UploadedMedia;
import vn.edu.hcmute.drss.repositories.MediaCleanupTaskRepository;

@ExtendWith(MockitoExtension.class)
class MediaCleanupQueueServiceTest {

    @Mock
    private MediaCleanupTaskRepository tasks;

    @Test
    void enqueueStoresOnlyDeletionIdentityAndRetryMetadata() {
        MediaCleanupQueueService service =
                new MediaCleanupQueueService(tasks);
        UploadedMedia uploaded = new UploadedMedia(
                "drss/dev/dat/public-id",
                "https://private.example.invalid/asset.mp4",
                MediaResourceType.VIDEO,
                "video/mp4",
                "mp4",
                1920,
                1080,
                null,
                4096
        );
        when(tasks.findByPublicId(uploaded.publicId()))
                .thenReturn(Optional.empty());

        service.enqueue(uploaded, "create compensation");

        ArgumentCaptor<MediaCleanupTask> capture =
                ArgumentCaptor.forClass(MediaCleanupTask.class);
        verify(tasks).saveAndFlush(capture.capture());

        MediaCleanupTask task = capture.getValue();
        assertThat(task.getPublicId()).isEqualTo(uploaded.publicId());
        assertThat(task.getResourceType()).isEqualTo(MediaResourceType.VIDEO);
        assertThat(task.getFailureCode())
                .isEqualTo("CREATE_COMPENSATION");
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getNextAttemptAt()).isNotNull();
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    void repeatedEnqueueReusesSameTaskAndIncrementsAttempt() {
        MediaCleanupQueueService service =
                new MediaCleanupQueueService(tasks);
        MediaCleanupTask existing = new MediaCleanupTask();
        existing.setPublicId("folder/asset");
        existing.setResourceType(MediaResourceType.IMAGE);
        existing.setAttemptCount(2);
        when(tasks.findByPublicId("folder/asset"))
                .thenReturn(Optional.of(existing));

        UploadedMedia uploaded = new UploadedMedia(
                "folder/asset",
                "https://example.invalid/asset.png",
                MediaResourceType.IMAGE,
                "image/png",
                "png",
                1,
                1,
                null,
                8
        );

        service.enqueue(uploaded, "CREATE_COMPENSATION");

        assertThat(existing.getAttemptCount()).isEqualTo(3);
        verify(tasks).saveAndFlush(existing);
    }
}