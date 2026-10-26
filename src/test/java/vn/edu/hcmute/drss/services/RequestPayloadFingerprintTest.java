package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.media.ValidatedMediaUpload;

class RequestPayloadFingerprintTest {
    private final RequestPayloadFingerprint fingerprint = new RequestPayloadFingerprint();

    @Test
    void changesInMediaBytesOrFieldsChangeDigest() {
        CreateCitizenRequest first = new CreateCitizenRequest();
        first.setDisasterTypeId(1L);
        first.setUrgency("HIGH");
        var image = new ValidatedMediaUpload(
                new byte[] {1, 2, 3}, "a.png", "image/png", MediaResourceType.IMAGE);
        String original = fingerprint.digest(first, List.of(image));
        assertThat(fingerprint.digest(first, List.of(image))).isEqualTo(original);
        assertThat(fingerprint.digest(first, List.of(
                new ValidatedMediaUpload(new byte[] {1, 2, 4},
                        "a.png", "image/png", MediaResourceType.IMAGE))))
                .isNotEqualTo(original);
        first.setDescription("different");
        assertThat(fingerprint.digest(first, List.of(image))).isNotEqualTo(original);
    }
}
