package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.media.UploadedMedia;
import vn.edu.hcmute.drss.media.ValidatedMediaUpload;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private AvatarUploadValidator validator;
    @Mock private AvatarCloudinaryStorage storage;
    @Mock private AvatarPersistenceService persistence;
    @Mock private MediaCleanupQueueService cleanupQueue;
    @InjectMocks private AvatarService service;

    private final MockMultipartFile file = new MockMultipartFile(
            "avatar", "avatar.png", "image/png", new byte[]{1});
    private final ValidatedMediaUpload validated = new ValidatedMediaUpload(
            new byte[]{1}, "avatar.png", "image/png", MediaResourceType.IMAGE);
    private final UploadedMedia uploaded = new UploadedMedia(
            "drss/dev/avatars/" + USER_ID + "/new",
            "https://example.com/avatar.png", MediaResourceType.IMAGE,
            "image/png", "png", null, null, null, 1);

    @Test
    void successfulReplacementDeletesPreviousAfterPersistence() {
        when(validator.validate(file)).thenReturn(validated);
        when(storage.upload(USER_ID, validated)).thenReturn(uploaded);
        when(persistence.replace(USER_ID, uploaded)).thenReturn("old-avatar");
        service.replace(USER_ID, file);
        verify(storage).delete("old-avatar");
        verifyNoInteractions(cleanupQueue);
    }

    @Test
    void failedDatabaseCommitCompensatesNewUpload() {
        when(validator.validate(file)).thenReturn(validated);
        when(storage.upload(USER_ID, validated)).thenReturn(uploaded);
        when(persistence.replace(USER_ID, uploaded))
                .thenThrow(new IllegalStateException("commit failed"));
        assertThatThrownBy(() -> service.replace(USER_ID, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("commit failed");
        verify(storage).delete(uploaded.publicId());
        verify(storage, never()).delete("old-avatar");
    }

    @Test
    void failedDeleteIsQueuedForDurableRetry() {
        when(validator.validate(file)).thenReturn(validated);
        when(storage.upload(USER_ID, validated)).thenReturn(uploaded);
        when(persistence.replace(USER_ID, uploaded)).thenReturn("old-avatar");
        doThrow(new IllegalStateException("remote unavailable"))
                .when(storage).delete("old-avatar");
        service.replace(USER_ID, file);
        verify(cleanupQueue).enqueue(any(UploadedMedia.class), eq("AVATAR_REPLACED"));
    }

    @Test
    void validationFailureCannotReachRemoteOrDatabase() {
        when(validator.validate(file)).thenThrow(
                new vn.edu.hcmute.drss.exceptions.InvalidMediaUploadException("invalid"));
        assertThatThrownBy(() -> service.replace(USER_ID, file))
                .isInstanceOf(vn.edu.hcmute.drss.exceptions.InvalidMediaUploadException.class);
        verifyNoInteractions(storage, persistence, cleanupQueue);
    }

    @Test
    void removalClearsDatabaseThenDeletesRemoteImage() {
        when(persistence.remove(USER_ID)).thenReturn("old-avatar");
        service.remove(USER_ID);
        verify(storage).delete("old-avatar");
    }
}