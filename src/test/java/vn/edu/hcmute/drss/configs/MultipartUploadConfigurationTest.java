package vn.edu.hcmute.drss.configs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.unit.DataSize;
import vn.edu.hcmute.drss.support.NoDatabaseRepositoryMocks;

@SpringBootTest
@ActiveProfiles("test")
@NoDatabaseRepositoryMocks
class MultipartUploadConfigurationTest {

    @Autowired
    private MultipartProperties multipart;

    @Autowired
    private MediaStorageProperties media;

    @Autowired
    private Environment environment;

    @Test
    void transportAllowsValidationBeforeBusinessUploadLimit() {
        long tenMiB = DataSize.ofMegabytes(10).toBytes();
        long sixtyFourMiB = DataSize.ofMegabytes(64).toBytes();

        assertThat(media.maxFileSize().toBytes())
                .isEqualTo(tenMiB);

        assertThat(multipart.getMaxFileSize().toBytes())
                .isEqualTo(sixtyFourMiB)
                .isGreaterThan(media.maxFileSize().toBytes());

        assertThat(multipart.getMaxRequestSize().toBytes())
                .isEqualTo(sixtyFourMiB);

        assertThat(multipart.isResolveLazily()).isTrue();

        DataSize maxSwallowSize = environment.getProperty(
                "server.tomcat.max-swallow-size",
                DataSize.class
        );

        assertThat(maxSwallowSize).isNotNull();
        assertThat(maxSwallowSize.toBytes())
                .isGreaterThanOrEqualTo(sixtyFourMiB);
    }
}