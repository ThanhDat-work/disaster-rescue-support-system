package vn.edu.hcmute.drss.media;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.drss.configs.MediaStorageProperties;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.exceptions.InvalidMediaUploadException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class MediaUploadValidator {

    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";
    private static final String WEBP = "image/webp";
    private static final String MP4 = "video/mp4";

    private static final Set<String> ALLOWED_TYPES =
            Set.of(JPEG, PNG, WEBP, MP4);

    private final MediaStorageProperties properties;

    public MediaUploadValidator(MediaStorageProperties properties) {
        this.properties = properties;
    }

    public List<ValidatedMediaUpload> validate(
            List<MultipartFile> files
    ) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        if (files.size() > properties.maxFilesPerRequest()) {
            throw invalid(
                    "M\u1ed7i thao t\u00e1c ch\u1ec9 \u0111\u01b0\u1ee3c t\u1ea3i l\u00ean t\u1ed1i \u0111a "
                            + properties.maxFilesPerRequest()
                            + " t\u1ec7p."
            );
        }

        List<ValidatedMediaUpload> validated = new ArrayList<>();
        int videoCount = 0;

        for (MultipartFile file : files) {
            ValidatedMediaUpload item = validateOne(file);
            if (item.resourceType() == MediaResourceType.VIDEO) {
                videoCount++;
            }
            validated.add(item);
        }

        if (videoCount > properties.maxVideoFilesPerRequest()) {
            throw invalid(
                    "M\u1ed7i thao t\u00e1c ch\u1ec9 \u0111\u01b0\u1ee3c t\u1ea3i l\u00ean t\u1ed1i \u0111a "
                            + properties.maxVideoFilesPerRequest()
                            + " video."
            );
        }

        return List.copyOf(validated);
    }

    private ValidatedMediaUpload validateOne(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("T\u1ec7p media kh\u00f4ng \u0111\u01b0\u1ee3c \u0111\u1ec3 tr\u1ed1ng.");
        }

        long maximum = properties.maxFileSize().toBytes();
        if (file.getSize() > maximum) {
            throw tooLarge();
        }

        String declaredType = normalizeContentType(file.getContentType());
        if (!ALLOWED_TYPES.contains(declaredType)) {
            throw invalid(
                    "Ch\u1ec9 h\u1ed7 tr\u1ee3 JPEG, PNG, WebP v\u00e0 MP4."
            );
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidMediaUploadException(
                    "Kh\u00f4ng th\u1ec3 \u0111\u1ecdc t\u1ec7p media.",
                    exception
            );
        }

        if (content.length == 0) {
            throw invalid("T\u1ec7p media kh\u00f4ng \u0111\u01b0\u1ee3c \u0111\u1ec3 tr\u1ed1ng.");
        }
        if (content.length > maximum) {
            throw tooLarge();
        }

        String detectedType = detectMimeType(content);
        if (!declaredType.equals(detectedType)) {
            throw invalid(
                    "N\u1ed9i dung t\u1ec7p kh\u00f4ng kh\u1edbp v\u1edbi lo\u1ea1i media khai b\u00e1o."
            );
        }

        MediaResourceType resourceType = MP4.equals(detectedType)
                ? MediaResourceType.VIDEO
                : MediaResourceType.IMAGE;

        return new ValidatedMediaUpload(
                content,
                safeFilename(file.getOriginalFilename()),
                detectedType,
                resourceType
        );
    }

    private String detectMimeType(byte[] content) {
        if (isJpeg(content)) {
            return JPEG;
        }
        if (isPng(content)) {
            return PNG;
        }
        if (isWebp(content)) {
            return WEBP;
        }
        if (isMp4(content)) {
            return MP4;
        }
        throw invalid(
                "Kh\u00f4ng nh\u1eadn di\u1ec7n \u0111\u01b0\u1ee3c \u0111\u1ecbnh d\u1ea1ng media."
        );
    }

    private boolean isJpeg(byte[] value) {
        return value.length >= 3
                && unsigned(value[0]) == 0xFF
                && unsigned(value[1]) == 0xD8
                && unsigned(value[2]) == 0xFF;
    }

    private boolean isPng(byte[] value) {
        int[] signature = {
                0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
        return hasSignature(value, 0, signature);
    }

    private boolean isWebp(byte[] value) {
        return hasAscii(value, 0, "RIFF")
                && hasAscii(value, 8, "WEBP");
    }

    private boolean isMp4(byte[] value) {
        return hasAscii(value, 4, "ftyp");
    }

    private boolean hasAscii(byte[] value, int offset, String expected) {
        if (value.length < offset + expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if (unsigned(value[offset + i]) != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasSignature(
            byte[] value,
            int offset,
            int[] expected
    ) {
        if (value.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (unsigned(value[offset + i]) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private String normalizeContentType(String contentType) {
        return contentType == null
                ? ""
                : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "media";
        }
        String normalized = filename.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator >= 0
                ? normalized.substring(separator + 1)
                : normalized;
    }

    private InvalidMediaUploadException tooLarge() {
        return invalid(
                "M\u1ed7i t\u1ec7p media kh\u00f4ng \u0111\u01b0\u1ee3c v\u01b0\u1ee3t qu\u00e1 10 MiB."
        );
    }

    private InvalidMediaUploadException invalid(String message) {
        return new InvalidMediaUploadException(message);
    }
}