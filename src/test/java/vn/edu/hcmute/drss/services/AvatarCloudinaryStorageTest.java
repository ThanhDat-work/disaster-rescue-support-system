package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;
import vn.edu.hcmute.drss.configs.MediaStorageProperties;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.media.CloudinaryGateway;
import vn.edu.hcmute.drss.media.MediaStorage;
import vn.edu.hcmute.drss.media.ValidatedMediaUpload;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarCloudinaryStorageTest {
    @Mock private CloudinaryGateway gateway;
    @Mock private MediaStorage mediaStorage;
    @Mock private MediaCleanupQueueService cleanupQueue;

    @Test
    void uploadsImageToEnvironmentAndUserScopedFolder() throws Exception {
        UUID userId = UUID.randomUUID();
        String folder = "drss/dev/avatars/" + userId;
        when(gateway.upload(any(), any())).thenReturn(Map.of(
                "public_id", folder + "/generated",
                "secure_url", "https://res.cloudinary.com/demo/avatar.png",
                "resource_type", "image", "format", "png"));
        var properties = new MediaStorageProperties("cloud", "key", "secret",
                "drss/dev/dat", DataSize.ofMegabytes(10), 5, 1);
        var storage = new AvatarCloudinaryStorage(gateway, mediaStorage,
                properties, cleanupQueue);
        var image = new ValidatedMediaUpload(new byte[]{1}, "avatar.png",
                "image/png", MediaResourceType.IMAGE);

        assertThat(storage.upload(userId, image).publicId())
                .isEqualTo(folder + "/generated");
        verify(gateway).upload(eq(new byte[]{1}),
                org.mockito.ArgumentMatchers.argThat(options ->
                        folder.equals(options.get("folder"))
                        && "image".equals(options.get("resource_type"))));
    }
}