package vn.edu.hcmute.drss.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.media.UploadedMedia;

import java.util.UUID;

@Service
public class AvatarService {
    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);
    private final AvatarUploadValidator validator;
    private final AvatarCloudinaryStorage storage;
    private final AvatarPersistenceService persistence;
    private final MediaCleanupQueueService cleanupQueue;

    public AvatarService(AvatarUploadValidator validator,
                         AvatarCloudinaryStorage storage,
                         AvatarPersistenceService persistence,
                         MediaCleanupQueueService cleanupQueue) {
        this.validator = validator;
        this.storage = storage;
        this.persistence = persistence;
        this.cleanupQueue = cleanupQueue;
    }

    public void replace(UUID userId, MultipartFile file) {
        var validated = validator.validate(file); // kiểm tra trước khi gọi Cloudinary
        UploadedMedia uploaded = storage.upload(userId, validated);
        String previous;
        try {
            previous = persistence.replace(userId, uploaded); // commit trước khi xóa cũ
        } catch (RuntimeException persistenceFailure) {
            deleteOrQueue(uploaded.publicId(), "AVATAR_DB_ROLLBACK");
            throw persistenceFailure;
        }
        if (previous != null && !previous.equals(uploaded.publicId())) {
            deleteOrQueue(previous, "AVATAR_REPLACED");
        }
    }

    public void remove(UUID userId) {
        String previous = persistence.remove(userId); // commit trước khi xóa cloud
        if (previous != null) {
            deleteOrQueue(previous, "AVATAR_REMOVED");
        }
    }

    private void deleteOrQueue(String publicId, String reason) {
        try {
            storage.delete(publicId);
        } catch (RuntimeException deleteFailure) {
            try {
                cleanupQueue.enqueue(new UploadedMedia(publicId, "",
                        MediaResourceType.IMAGE, "", "", null, null,
                        null, 0), reason);
            } catch (RuntimeException enqueueFailure) {
                log.error("Avatar cleanup could not be queued: {}", reason,
                        enqueueFailure);
                throw new IllegalStateException(
                        "Không thể ghi hàng đợi cleanup avatar.", enqueueFailure);
            }
        }
    }
}