package vn.edu.hcmute.drss.configs;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaStoragePropertiesTest {

    @Test
    void acceptsValidConfigurationAndNormalizesFolder() {
        MediaStorageProperties properties = new MediaStorageProperties(
                " demo-cloud ",
                " api-key ",
                " api-secret ",
                "drss\\local\\dat",
                DataSize.ofMegabytes(10),
                5,
                1
        );

        assertThat(properties.cloudName()).isEqualTo("demo-cloud");
        assertThat(properties.apiKey()).isEqualTo("api-key");
        assertThat(properties.apiSecret()).isEqualTo("api-secret");
        assertThat(properties.folder()).isEqualTo("drss/local/dat");
        assertThat(properties.maxFileSize())
                .isEqualTo(DataSize.ofMegabytes(10));
    }

    @Test
    void rejectsBlankCredentials() {
        assertThatThrownBy(() -> properties(" ", "key", "secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties("cloud", " ", "secret"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties("cloud", "key", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsafeFolder() {
        assertThatThrownBy(() -> new MediaStorageProperties(
                "cloud", "key", "secret", "../prod",
                DataSize.ofMegabytes(10), 5, 1
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new MediaStorageProperties(
                "cloud", "key", "secret", "/drss/prod",
                DataSize.ofMegabytes(10), 5, 1
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> new MediaStorageProperties(
                "cloud", "key", "secret", "drss/test",
                DataSize.ofBytes(0), 5, 1
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new MediaStorageProperties(
                "cloud", "key", "secret", "drss/test",
                DataSize.ofMegabytes(10), 0, 0
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new MediaStorageProperties(
                "cloud", "key", "secret", "drss/test",
                DataSize.ofMegabytes(10), 5, 6
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private MediaStorageProperties properties(
            String cloud,
            String key,
            String secret
    ) {
        return new MediaStorageProperties(
                cloud,
                key,
                secret,
                "drss/test",
                DataSize.ofMegabytes(10),
                5,
                1
        );
    }
}