package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.entities.MediaAsset;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.User;
import vn.edu.hcmute.drss.media.UploadedMedia;
import vn.edu.hcmute.drss.repositories.MediaAssetRepository;

@ExtendWith(MockitoExtension.class)
class CitizenRequestMediaPersistenceServiceTest {

    @Mock
    private CitizenRequestService citizenRequests;
    @Mock
    private MediaAssetRepository mediaAssets;
    @Mock
    private BusinessNotificationService notifications;

    private CitizenRequestMediaPersistenceService service;
    private UUID userId;
    private CreateCitizenRequest form;
    private RescueRequest request;

    @BeforeEach
    void setUp() {
        service = new CitizenRequestMediaPersistenceService(
                citizenRequests, mediaAssets, notifications
        );
        userId = UUID.randomUUID();
        form = new CreateCitizenRequest();
        request = new RescueRequest();
        User owner = new User();
        owner.setId(userId);
        request.setUser(owner);
        when(citizenRequests.create(userId, form)).thenReturn(request);
    }

    @Test
    void noUploadsCreatesRequestWithoutMediaRows() {
        assertThat(service.create(userId, form, List.of()))
                .isSameAs(request);
        verify(mediaAssets, never()).saveAllAndFlush(
                org.mockito.ArgumentMatchers.any()
        );
        verify(notifications).notifyNewRequest(request);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsEveryCloudinaryFieldToMediaAsset() {
        UploadedMedia uploaded = new UploadedMedia(
                "drss/dev/dat/asset",
                "https://example.invalid/asset.mp4",
                MediaResourceType.VIDEO,
                "video/mp4",
                "mp4",
                1920,
                1080,
                new BigDecimal("12.345"),
                4096
        );

        assertThat(service.create(userId, form, List.of(uploaded)))
                .isSameAs(request);

        ArgumentCaptor<Iterable<MediaAsset>> capture =
                ArgumentCaptor.forClass(Iterable.class);
        verify(mediaAssets).saveAllAndFlush(capture.capture());
        verify(notifications).notifyNewRequest(request);

        MediaAsset asset = capture.getValue().iterator().next();
        assertThat(asset.getRequest()).isSameAs(request);
        assertThat(asset.getUploaderUser()).isSameAs(request.getUser());
        assertThat(asset.getPublicId()).isEqualTo(uploaded.publicId());
        assertThat(asset.getSecureUrl()).isEqualTo(uploaded.secureUrl());
        assertThat(asset.getResourceType()).isEqualTo(MediaResourceType.VIDEO);
        assertThat(asset.getMimeType()).isEqualTo("video/mp4");
        assertThat(asset.getFormat()).isEqualTo("mp4");
        assertThat(asset.getWidth()).isEqualTo(1920);
        assertThat(asset.getHeight()).isEqualTo(1080);
        assertThat(asset.getDurationSeconds())
                .isEqualByComparingTo("12.345");
        assertThat(asset.getBytes()).isEqualTo(4096);
    }
}