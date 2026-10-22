package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import vn.edu.hcmute.drss.entities.MediaCleanupTask;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.exceptions.MediaStorageException;
import vn.edu.hcmute.drss.media.MediaStorage;
import vn.edu.hcmute.drss.repositories.MediaCleanupTaskRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaCleanupRetryServiceTest {

    private static final Instant NOW = Instant.parse(
            "2026-10-22T00:00:00Z"
    );

    @Mock
    private MediaCleanupTaskRepository tasks;
    @Mock
    private MediaStorage storage;

    private MediaCleanupRetryService service;

    @BeforeEach
    void setUp() {
        service = new MediaCleanupRetryService(
                tasks,
                storage,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void successfulRetryMarksTaskCompleted() {
        MediaCleanupTask task = task();
        when(tasks
                .findByCompletedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
                        any(LocalDateTime.class),
                        any(Pageable.class)
                )).thenReturn(List.of(task));

        int completed = service.retryDueTasks(25);

        assertThat(completed).isEqualTo(1);
        assertThat(task.getAttemptCount()).isEqualTo(2);
        assertThat(task.getCompletedAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        verify(storage).delete(
                task.getPublicId(),
                MediaResourceType.VIDEO
        );
        verify(tasks).saveAllAndFlush(List.of(task));
    }

    @Test
    void failedRetryIsRescheduledWithoutLeakingStorageError() {
        MediaCleanupTask task = task();
        LocalDateTime originalDue = task.getNextAttemptAt();
        when(tasks
                .findByCompletedAtIsNullAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(
                        any(LocalDateTime.class),
                        any(Pageable.class)
                )).thenReturn(List.of(task));
        doThrow(new MediaStorageException("storage unavailable"))
                .when(storage)
                .delete(task.getPublicId(), task.getResourceType());

        int completed = service.retryDueTasks(25);

        assertThat(completed).isZero();
        assertThat(task.getAttemptCount()).isEqualTo(2);
        assertThat(task.getFailureCode()).isEqualTo("RETRY_FAILED");
        assertThat(task.getCompletedAt()).isNull();
        assertThat(task.getNextAttemptAt()).isAfter(originalDue);
        verify(tasks).saveAllAndFlush(List.of(task));
    }

    @Test
    void invalidBatchSizeIsRejectedBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.retryDueTasks(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.retryDueTasks(101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MediaCleanupTask task() {
        MediaCleanupTask task = new MediaCleanupTask();
        task.setPublicId("drss/local/dat/retry-video");
        task.setResourceType(MediaResourceType.VIDEO);
        task.setFailureCode("CREATE_COMPENSATION");
        task.setAttemptCount(1);
        task.setNextAttemptAt(
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
                        .minusMinutes(1)
        );
        return task;
    }
}