package vn.edu.hcmute.drss.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.drss.configs.MediaStorageProperties;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.exceptions.InvalidMediaUploadException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUploadValidatorTest {

    private MediaUploadValidator validator;

    @BeforeEach
    void setUp() {
        MediaStorageProperties properties = new MediaStorageProperties(
                "cloud",
                "key",
                "secret",
                "drss/test",
                DataSize.ofMegabytes(10),
                5,
                1
        );
        validator = new MediaUploadValidator(properties);
    }

    @Test
    void acceptsSupportedImagesAndOneVideo() {
        List<ValidatedMediaUpload> result = validator.validate(List.of(
                file("a.jpg", "image/jpeg", jpeg()),
                file("b.png", "image/png", png()),
                file("c.webp", "image/webp", webp()),
                file("d.mp4", "video/mp4", mp4())
        ));

        assertThat(result).hasSize(4);
        assertThat(result.get(0).resourceType())
                .isEqualTo(MediaResourceType.IMAGE);
        assertThat(result.get(3).resourceType())
                .isEqualTo(MediaResourceType.VIDEO);
    }

    @Test
    void noFilesIsValidAndReturnsImmutableEmptyList() {
        assertThat(validator.validate(null)).isEmpty();
        assertThat(validator.validate(List.of())).isEmpty();
    }

    @Test
    void rejectsMoreThanFiveFiles() {
        List<MultipartFile> files = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            files.add(file(index + ".jpg", "image/jpeg", jpeg()));
        }

        assertThatThrownBy(() -> validator.validate(files))
                .isInstanceOf(InvalidMediaUploadException.class);
    }

    @Test
    void rejectsMoreThanOneVideo() {
        assertThatThrownBy(() -> validator.validate(List.of(
                file("a.mp4", "video/mp4", mp4()),
                file("b.mp4", "video/mp4", mp4())
        ))).isInstanceOf(InvalidMediaUploadException.class);
    }

    @Test
    void rejectsSpoofedContentType() {
        assertThatThrownBy(() -> validator.validate(List.of(
                file("fake.jpg", "image/jpeg", mp4())
        )))
                .isInstanceOf(InvalidMediaUploadException.class)
                .hasMessageContaining("kh\u00f4ng kh\u1edbp");
    }

    @Test
    void rejectsUnknownFormatAndEmptyFile() {
        assertThatThrownBy(() -> validator.validate(List.of(
                file("data.bin", "image/png", new byte[]{1, 2, 3})
        ))).isInstanceOf(InvalidMediaUploadException.class);

        assertThatThrownBy(() -> validator.validate(List.of(
                file("empty.jpg", "image/jpeg", new byte[0])
        ))).isInstanceOf(InvalidMediaUploadException.class);
    }

    @Test
    void rejectsFileLargerThanTenMiB() {
        byte[] oversized = new byte[
                Math.toIntExact(DataSize.ofMegabytes(10).toBytes()) + 1
        ];
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;
        oversized[2] = (byte) 0xFF;

        assertThatThrownBy(() -> validator.validate(List.of(
                file("large.jpg", "image/jpeg", oversized)
        )))
                .isInstanceOf(InvalidMediaUploadException.class)
                .hasMessageContaining("10 MiB");
    }

    private MockMultipartFile file(
            String name,
            String contentType,
            byte[] content
    ) {
        return new MockMultipartFile(
                "mediaFiles",
                name,
                contentType,
                content
        );
    }

    private byte[] jpeg() {
        return new byte[]{
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };
    }

    private byte[] png() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
    }

    private byte[] webp() {
        return new byte[]{
                'R', 'I', 'F', 'F', 0, 0, 0, 0,
                'W', 'E', 'B', 'P'
        };
    }

    private byte[] mp4() {
        return new byte[]{
                0, 0, 0, 24,
                'f', 't', 'y', 'p',
                'i', 's', 'o', 'm'
        };
    }
}