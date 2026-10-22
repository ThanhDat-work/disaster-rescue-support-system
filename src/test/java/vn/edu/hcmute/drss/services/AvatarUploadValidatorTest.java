package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.hcmute.drss.exceptions.InvalidMediaUploadException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarUploadValidatorTest {
    private final AvatarUploadValidator validator = new AvatarUploadValidator();

    @Test
    void pngSignatureIsAccepted() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0};
        var upload = new MockMultipartFile("avatar", "ok.png", "image/png", png);
        assertThat(validator.validate(upload).mimeType()).isEqualTo("image/png");
    }

    @Test
    void videoAndForgedPngAreRejected() {
        var video = new MockMultipartFile("avatar", "clip.mp4", "video/mp4", new byte[]{1});
        var forged = new MockMultipartFile("avatar", "fake.png", "image/png", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> validator.validate(video))
                .isInstanceOf(InvalidMediaUploadException.class);
        assertThatThrownBy(() -> validator.validate(forged))
                .isInstanceOf(InvalidMediaUploadException.class);
    }

    @Test
    void overTwoMiBIsRejectedBeforeReadingBytes() {
        byte[] bytes = new byte[2 * 1024 * 1024 + 1];
        var upload = new MockMultipartFile("avatar", "large.png", "image/png", bytes);
        assertThatThrownBy(() -> validator.validate(upload))
                .isInstanceOf(InvalidMediaUploadException.class)
                .hasMessageContaining("2 MiB");
    }
}