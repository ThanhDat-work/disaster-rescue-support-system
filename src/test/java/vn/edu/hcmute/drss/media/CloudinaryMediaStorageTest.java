package vn.edu.hcmute.drss.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import vn.edu.hcmute.drss.configs.MediaStorageProperties;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.exceptions.MediaStorageException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryMediaStorageTest {

    @Mock
    private CloudinaryGateway gateway;

    private CloudinaryMediaStorage storage;

    @BeforeEach
    void setUp() {
        MediaStorageProperties properties = new MediaStorageProperties(
                "cloud",
                "key",
                "secret",
                "drss/test/dat",
                DataSize.ofMegabytes(10),
                5,
                1
        );
        storage = new CloudinaryMediaStorage(gateway, properties);
    }

    @Test
    void uploadsImageWithSafeServerOptions() throws Exception {
        when(gateway.upload(any(), any())).thenReturn(Map.of(
                "public_id", "drss/test/dat/generated-id",
                "secure_url", "https://res.cloudinary.com/demo/image/upload/id.jpg",
                "resource_type", "image",
                "format", "jpg",
                "width", 640,
                "height", 480,
                "bytes", 1234
        ));

        UploadedMedia result = storage.upload(validated(
                MediaResourceType.IMAGE,
                "image/jpeg"
        ));

        assertThat(result.publicId())
                .isEqualTo("drss/test/dat/generated-id");
        assertThat(result.resourceType())
                .isEqualTo(MediaResourceType.IMAGE);
        assertThat(result.bytes()).isEqualTo(1234);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> options =
                ArgumentCaptor.forClass(Map.class);
        verify(gateway).upload(any(), options.capture());

        assertThat(options.getValue())
                .containsEntry("folder", "drss/test/dat")
                .containsEntry("resource_type", "image")
                .containsEntry("overwrite", false)
                .containsEntry("use_filename", false);
    }

    @Test
    void mapsVideoDurationAndUsesVideoResourceType() throws Exception {
        when(gateway.upload(any(), any())).thenReturn(Map.of(
                "public_id", "drss/test/dat/video-id",
                "secure_url", "https://res.cloudinary.com/demo/video/upload/id.mp4",
                "resource_type", "video",
                "format", "mp4",
                "width", 1280,
                "height", 720,
                "duration", 12.345,
                "bytes", 4321
        ));

        UploadedMedia result = storage.upload(validated(
                MediaResourceType.VIDEO,
                "video/mp4"
        ));

        assertThat(result.durationSeconds())
                .isEqualByComparingTo(new BigDecimal("12.345"));
        assertThat(result.resourceType())
                .isEqualTo(MediaResourceType.VIDEO);
    }

    @Test
    void deleteUsesCorrectResourceTypeAndInvalidatesCache()
            throws Exception {
        when(gateway.destroy(any(), any()))
                .thenReturn(Map.of("result", "ok"));

        storage.delete("drss/test/video-id", MediaResourceType.VIDEO);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> options =
                ArgumentCaptor.forClass(Map.class);
        verify(gateway).destroy(
                eq("drss/test/video-id"),
                options.capture()
        );
        assertThat(options.getValue())
                .containsEntry("resource_type", "video")
                .containsEntry("invalidate", true);
    }

    @Test
    void deleteTreatsNotFoundAsIdempotentSuccess() throws Exception {
        when(gateway.destroy(any(), any()))
                .thenReturn(Map.of("result", "not found"));

        storage.delete("already-gone", MediaResourceType.IMAGE);
    }

    @Test
    void rejectsInsecureOrMismatchedUploadResponse() throws Exception {
        when(gateway.upload(any(), any())).thenReturn(Map.of(
                "public_id", "id",
                "secure_url", "http://example.invalid/id.jpg",
                "resource_type", "image",
                "format", "jpg",
                "bytes", 10
        ));

        assertThatThrownBy(() -> storage.upload(validated(
                MediaResourceType.IMAGE,
                "image/jpeg"
        ))).isInstanceOf(MediaStorageException.class);
    }

    @Test
    void wrapsGatewayIoFailureWithoutLeakingCredentials()
            throws Exception {
        when(gateway.upload(any(), any()))
                .thenThrow(new IOException("transport unavailable"));

        assertThatThrownBy(() -> storage.upload(validated(
                MediaResourceType.IMAGE,
                "image/jpeg"
        )))
                .isInstanceOf(MediaStorageException.class)
                .hasMessage("Cloudinary upload failed")
                .hasMessageNotContaining("secret");
    }

    @Test
    void rejectsUnexpectedDeleteResult() throws Exception {
        when(gateway.destroy(any(), any()))
                .thenReturn(Map.of("result", "failed"));

        assertThatThrownBy(() -> storage.delete(
                "id",
                MediaResourceType.IMAGE
        )).isInstanceOf(MediaStorageException.class);
    }

    private ValidatedMediaUpload validated(
            MediaResourceType type,
            String mimeType
    ) {
        return new ValidatedMediaUpload(
                new byte[]{1, 2, 3},
                "demo",
                mimeType,
                type
        );
    }
}