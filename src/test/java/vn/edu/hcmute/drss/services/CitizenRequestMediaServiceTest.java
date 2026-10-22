package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.exceptions.MediaStorageException;
import vn.edu.hcmute.drss.media.MediaStorage;
import vn.edu.hcmute.drss.media.MediaUploadValidator;
import vn.edu.hcmute.drss.media.UploadedMedia;
import vn.edu.hcmute.drss.media.ValidatedMediaUpload;

@ExtendWith(MockitoExtension.class)
class CitizenRequestMediaServiceTest {

    @Mock
    private MediaUploadValidator validator;
    @Mock
    private MediaStorage storage;
    @Mock
    private CitizenRequestMediaPersistenceService persistence;
    @Mock
    private MediaCleanupQueueService cleanupQueue;

    private CitizenRequestMediaService service;
    private UUID userId;
    private CreateCitizenRequest form;

    @BeforeEach
    void setUp() {
        service = new CitizenRequestMediaService(
                validator, storage, persistence, cleanupQueue
        );
        userId = UUID.randomUUID();
        form = new CreateCitizenRequest();
        form.setDisasterTypeId(7L);
        form.setUrgency("HIGH");
    }

    @Test
    void noMediaDelegatesToTransactionalPersistence() {
        RescueRequest expected = new RescueRequest();
        when(validator.validate(List.of())).thenReturn(List.of());
        when(persistence.create(userId, form, List.of()))
                .thenReturn(expected);

        assertThat(service.create(userId, form, List.of()))
                .isSameAs(expected);

        verify(storage, never()).upload(any());
        verify(storage, never()).delete(any(), any());
    }

    @Test
    void successfulUploadsArePersistedInOriginalOrder() {
        List<MultipartFile> files = List.of(file("a.png"), file("b.png"));
        ValidatedMediaUpload firstInput = validated("a.png");
        ValidatedMediaUpload secondInput = validated("b.png");
        UploadedMedia first = uploaded("folder/first");
        UploadedMedia second = uploaded("folder/second");
        RescueRequest expected = new RescueRequest();

        when(validator.validate(files))
                .thenReturn(List.of(firstInput, secondInput));
        when(storage.upload(firstInput)).thenReturn(first);
        when(storage.upload(secondInput)).thenReturn(second);
        when(persistence.create(
                userId, form, List.of(first, second)
        )).thenReturn(expected);

        assertThat(service.create(userId, form, files)).isSameAs(expected);
        verify(persistence).create(userId, form, List.of(first, second));
        verify(storage, never()).delete(any(), any());
    }

    @Test
    void secondUploadFailureDeletesFirstAndSkipsDatabase() {
        List<MultipartFile> files = List.of(file("a.png"), file("b.png"));
        ValidatedMediaUpload firstInput = validated("a.png");
        ValidatedMediaUpload secondInput = validated("b.png");
        UploadedMedia first = uploaded("folder/first");

        when(validator.validate(files))
                .thenReturn(List.of(firstInput, secondInput));
        when(storage.upload(firstInput)).thenReturn(first);
        when(storage.upload(secondInput))
                .thenThrow(new MediaStorageException("upload failed"));

        assertThatThrownBy(() -> service.create(userId, form, files))
                .isInstanceOf(MediaStorageException.class)
                .hasMessageContaining("media");

        verify(storage).delete(
                first.publicId(), first.resourceType()
        );
        verify(persistence, never()).create(any(), any(), any());
    }

    @Test
    void databaseFailureDeletesAllUploadsInReverseOrder() {
        List<MultipartFile> files = List.of(file("a.png"), file("b.png"));
        ValidatedMediaUpload firstInput = validated("a.png");
        ValidatedMediaUpload secondInput = validated("b.png");
        UploadedMedia first = uploaded("folder/first");
        UploadedMedia second = uploaded("folder/second");

        when(validator.validate(files))
                .thenReturn(List.of(firstInput, secondInput));
        when(storage.upload(firstInput)).thenReturn(first);
        when(storage.upload(secondInput)).thenReturn(second);
        when(persistence.create(eq(userId), eq(form), any()))
                .thenThrow(new IllegalStateException("database failed"));

        assertThatThrownBy(() -> service.create(userId, form, files))
                .isInstanceOf(MediaStorageException.class);

        InOrder cleanupOrder = inOrder(storage);
        cleanupOrder.verify(storage).delete(
                second.publicId(), second.resourceType()
        );
        cleanupOrder.verify(storage).delete(
                first.publicId(), first.resourceType()
        );
    }

    @Test
    void failedImmediateDeleteCreatesDurableCleanupTask() {
        List<MultipartFile> files = List.of(file("a.png"));
        ValidatedMediaUpload input = validated("a.png");
        UploadedMedia uploaded = uploaded("folder/pending-cleanup");

        when(validator.validate(files)).thenReturn(List.of(input));
        when(storage.upload(input)).thenReturn(uploaded);
        when(persistence.create(eq(userId), eq(form), any()))
                .thenThrow(new IllegalStateException("database failed"));
        org.mockito.Mockito.doThrow(
                new MediaStorageException("delete failed")
        ).when(storage).delete(
                uploaded.publicId(), uploaded.resourceType()
        );

        assertThatThrownBy(() -> service.create(userId, form, files))
                .isInstanceOf(MediaStorageException.class);

        verify(cleanupQueue).enqueue(
                uploaded,
                CitizenRequestMediaService.CREATE_COMPENSATION
        );
    }

    private MockMultipartFile file(String filename) {
        return new MockMultipartFile(
                "mediaFiles",
                filename,
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A}
        );
    }

    private ValidatedMediaUpload validated(String filename) {
        return new ValidatedMediaUpload(
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47,
                        0x0D, 0x0A, 0x1A, 0x0A},
                filename,
                "image/png",
                MediaResourceType.IMAGE
        );
    }

    private UploadedMedia uploaded(String publicId) {
        return new UploadedMedia(
                publicId,
                "https://example.invalid/" + publicId + ".png",
                MediaResourceType.IMAGE,
                "image/png",
                "png",
                320,
                240,
                (BigDecimal) null,
                1024
        );
    }
}