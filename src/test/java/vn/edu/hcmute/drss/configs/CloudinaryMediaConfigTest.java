package vn.edu.hcmute.drss.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class CloudinaryMediaConfigTest {
    private final CloudinaryMediaConfig configuration = new CloudinaryMediaConfig();

    @Test
    void setsSdkHttp5TimeoutInSeconds() {
        var sdk = configuration.cloudinary(properties(), 60);
        assertThat(sdk.config.timeout).isEqualTo(60);
    }

    @Test
    void rejectsUnboundedTimeout() {
        assertThatThrownBy(() -> configuration.cloudinary(properties(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MediaStorageProperties properties() {
        return new MediaStorageProperties("test-cloud", "test-key", "test-secret",
                "drss/it", DataSize.ofMegabytes(10), 5, 1);
    }
}
