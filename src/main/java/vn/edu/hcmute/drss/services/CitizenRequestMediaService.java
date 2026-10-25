package vn.edu.hcmute.drss.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.exceptions.MediaStorageException;
import vn.edu.hcmute.drss.media.MediaStorage;
import vn.edu.hcmute.drss.media.MediaUploadValidator;
import vn.edu.hcmute.drss.media.UploadedMedia;
import vn.edu.hcmute.drss.media.ValidatedMediaUpload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class CitizenRequestMediaService {

    static final String CREATE_COMPENSATION = "CREATE_COMPENSATION";

    private static final Logger LOGGER = LoggerFactory.getLogger(
            CitizenRequestMediaService.class
    );

    private final MediaUploadValidator validator;
    private final MediaStorage storage;
    private final CitizenRequestMediaPersistenceService persistence;
    private final MediaCleanupQueueService cleanupQueue;

    public CitizenRequestMediaService(
            MediaUploadValidator validator,
            MediaStorage storage,
            CitizenRequestMediaPersistenceService persistence,
            MediaCleanupQueueService cleanupQueue
    ) {
        this.validator = validator;
        this.storage = storage;
        this.persistence = persistence;
        this.cleanupQueue = cleanupQueue;
    }

    public RescueRequest create(
            UUID userId,
            CreateCitizenRequest form,
            List<MultipartFile> files
    ) {
        return createValidated(userId, null, form, validator.validate(files));
    }

    public RescueRequest createAdmitted(UUID userId, UUID key,
            CreateCitizenRequest form, List<ValidatedMediaUpload> validated) {
        if (key == null) throw new IllegalArgumentException("Operation key required");
        return createValidated(userId, key, form, validated);
    }

    private RescueRequest createValidated(UUID userId, UUID key,
            CreateCitizenRequest form, List<ValidatedMediaUpload> validated) {
        List<UploadedMedia> uploaded = new ArrayList<>(validated.size());

        try {
            for (ValidatedMediaUpload item : validated) {
                UploadedMedia result = storage.upload(item);
                if (result == null) {
                    throw new MediaStorageException(
                            "Cloudinary upload returned no metadata"
                    );
                }
                uploaded.add(result);
            }
        } catch (RuntimeException uploadFailure) {
            compensate(uploaded, uploadFailure);
            throw publicStorageFailure(uploadFailure);
        }

        try {
            return key == null
                    ? persistence.create(userId, form, uploaded)
                    : persistence.createAdmitted(userId, key, form, uploaded);
        } catch (IllegalArgumentException businessFailure) {
            compensate(uploaded, businessFailure);
            throw businessFailure;
        } catch (RuntimeException persistenceFailure) {
            compensate(uploaded, persistenceFailure);
            throw new MediaStorageException(
                    "Kh\u00f4ng th\u1ec3 l\u01b0u y\u00eau c\u1ea7u k\u00e8m media. "
                            + "Vui l\u00f2ng th\u1eed l\u1ea1i.",
                    persistenceFailure
            );
        }
    }

    private void compensate(
            List<UploadedMedia> uploaded,
            RuntimeException originalFailure
    ) {
        if (uploaded.isEmpty()) {
            return;
        }

        List<UploadedMedia> reverse = new ArrayList<>(uploaded);
        Collections.reverse(reverse);
        int queued = 0;

        for (UploadedMedia item : reverse) {
            try {
                storage.delete(item.publicId(), item.resourceType());
            } catch (RuntimeException deleteFailure) {
                originalFailure.addSuppressed(deleteFailure);
                try {
                    cleanupQueue.enqueue(item, CREATE_COMPENSATION);
                    queued++;
                } catch (RuntimeException queueFailure) {
                    deleteFailure.addSuppressed(queueFailure);
                }
            }
        }

        if (queued > 0) {
            LOGGER.warn(
                    "Queued {} media cleanup task(s) after failed request creation",
                    queued
            );
        }
    }

    private MediaStorageException publicStorageFailure(
            RuntimeException cause
    ) {
        return new MediaStorageException(
                "Kh\u00f4ng th\u1ec3 t\u1ea3i media l\u00ean. "
                        + "Vui l\u00f2ng th\u1eed l\u1ea1i.",
                cause
        );
    }
}
