package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Component;
import vn.edu.hcmute.drss.configs.MediaStorageProperties;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.exceptions.MediaStorageException;
import vn.edu.hcmute.drss.media.CloudinaryGateway;
import vn.edu.hcmute.drss.media.MediaStorage;
import vn.edu.hcmute.drss.media.UploadedMedia;
import vn.edu.hcmute.drss.media.ValidatedMediaUpload;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
public class AvatarCloudinaryStorage {
    private final CloudinaryGateway gateway;
    private final MediaStorage mediaStorage;
    private final MediaStorageProperties properties;
    private final MediaCleanupQueueService cleanupQueue;

    public AvatarCloudinaryStorage(CloudinaryGateway gateway,
                                   MediaStorage mediaStorage,
                                   MediaStorageProperties properties,
                                   MediaCleanupQueueService cleanupQueue) {
        this.gateway = gateway;
        this.mediaStorage = mediaStorage;
        this.properties = properties;
        this.cleanupQueue = cleanupQueue;
    }

    public UploadedMedia upload(UUID userId, ValidatedMediaUpload image) {
        if (userId == null || image == null
                || image.resourceType() != MediaResourceType.IMAGE) {
            throw new IllegalArgumentException("Avatar image and user are required.");
        }
        String folder = avatarFolder(userId);
        Map<String, Object> response;
        try {
            response = gateway.upload(image.content(), Map.of(
                    "folder", folder, "resource_type", "image",
                    "overwrite", false, "unique_filename", true,
                    "use_filename", false
            ));
        } catch (IOException exception) {
            throw new MediaStorageException("Cloudinary avatar upload failed", exception);
        }
        if (response == null) {
            throw new MediaStorageException("Cloudinary avatar upload returned no response");
        }
        String publicId = value(response, "public_id");
        String url = value(response, "secure_url");
        String resource = value(response, "resource_type");
        if (!publicId.startsWith(folder + "/")
                || !url.startsWith("https://")
                || !"image".equals(resource)
                || publicId.length() > 255 || url.length() > 500) {
            // Chỉ xóa ID nằm đúng folder của user; không xóa ID Cloudinary lạ.
            if (publicId.startsWith(folder + "/") && "image".equals(resource)) {
                try {
                    mediaStorage.delete(publicId, MediaResourceType.IMAGE);
                } catch (RuntimeException deleteFailure) {
                    cleanupQueue.enqueue(new UploadedMedia(publicId, "",
                            MediaResourceType.IMAGE, "", "", null, null,
                            null, 0), "AVATAR_INVALID_RESPONSE");
                }
            }
            throw new MediaStorageException("Cloudinary avatar response is invalid");
        }
        return new UploadedMedia(publicId, url, MediaResourceType.IMAGE,
                image.mimeType(), value(response, "format"), null, null,
                null, image.content().length);
    }

    public void delete(String publicId) {
        mediaStorage.delete(publicId, MediaResourceType.IMAGE);
    }

    private String avatarFolder(UUID userId) {
        String[] segments = properties.folder().split("/");
        if (segments.length < 2 || !"drss".equals(segments[0])
                || !segments[1].matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException(
                    "CLOUDINARY_FOLDER phải theo mẫu drss/{environment}/..."
            );
        }
        return "drss/" + segments[1] + "/avatars/" + userId;
    }

    private String value(Map<String, Object> response, String key) {
        Object raw = response.get(key);
        return raw == null ? "" : raw.toString().strip();
    }
}