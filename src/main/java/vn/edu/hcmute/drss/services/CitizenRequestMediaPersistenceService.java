package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.entities.MediaAsset;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.media.UploadedMedia;
import vn.edu.hcmute.drss.repositories.MediaAssetRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CitizenRequestMediaPersistenceService {

    private final CitizenRequestService citizenRequests;
    private final MediaAssetRepository mediaAssets;
    private final BusinessNotificationService notifications;
    private final RequestCreationGateService gate;

    public CitizenRequestMediaPersistenceService(
        CitizenRequestService citizenRequests,
        MediaAssetRepository mediaAssets,
        BusinessNotificationService notifications,
        RequestCreationGateService gate
    ) {
        this.citizenRequests = citizenRequests;
        this.mediaAssets = mediaAssets;
        this.notifications = notifications;
        this.gate = gate;
    }

    @Transactional
    public RescueRequest create(
            UUID userId,
            CreateCitizenRequest form,
            List<UploadedMedia> uploadedMedia
    ) {
        return persist(userId, null, form, uploadedMedia);
    }

    @Transactional
    public RescueRequest createAdmitted(UUID userId, UUID key,
            CreateCitizenRequest form, List<UploadedMedia> uploadedMedia) {
        if (key == null) throw new IllegalArgumentException("Operation key required");
        return persist(userId, key, form, uploadedMedia);
    }

    private RescueRequest persist(UUID userId, UUID key,
            CreateCitizenRequest form, List<UploadedMedia> uploadedMedia) {
        RescueRequest request = citizenRequests.create(userId, form);
        List<UploadedMedia> uploaded = uploadedMedia == null
                ? List.of()
                : List.copyOf(uploadedMedia);

        if (!uploaded.isEmpty()) {
            User uploader = Objects.requireNonNull(
                    request.getUser(),
                    "Created request must have an owner"
            );

            List<MediaAsset> assets = new ArrayList<>(uploaded.size());
            for (UploadedMedia item : uploaded) {
                assets.add(toEntity(request, uploader, item));
            }

            mediaAssets.saveAllAndFlush(assets);
        }

        notifications.notifyNewRequest(request);
        if (key != null) gate.complete(userId, key, request);
        return request;
    }

    private MediaAsset toEntity(
            RescueRequest request,
            User uploader,
            UploadedMedia uploaded
    ) {
        Objects.requireNonNull(uploaded, "Uploaded media is required");

        MediaAsset asset = new MediaAsset();
        asset.setRequest(request);
        asset.setUploaderUser(uploader);
        asset.setPublicId(requireText(uploaded.publicId(), "Public ID"));
        asset.setSecureUrl(requireText(uploaded.secureUrl(), "Secure URL"));
        asset.setResourceType(Objects.requireNonNull(
                uploaded.resourceType(),
                "Resource type is required"
        ));
        asset.setMimeType(requireText(uploaded.mimeType(), "MIME type"));
        asset.setFormat(requireText(uploaded.format(), "Format"));
        asset.setWidth(uploaded.width());
        asset.setHeight(uploaded.height());
        asset.setDurationSeconds(uploaded.durationSeconds());
        asset.setBytes(uploaded.bytes());
        return asset;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
