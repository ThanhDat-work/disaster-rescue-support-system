package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.exceptions.InvalidMediaUploadException;
import vn.edu.hcmute.drss.media.ValidatedMediaUpload;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Component
public class AvatarUploadValidator {
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp"
    );

    public ValidatedMediaUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Vui lòng chọn một ảnh đại diện.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw invalid("Ảnh đại diện không được vượt quá 2 MiB.");
        }
        String mime = file.getContentType() == null
                ? "" : file.getContentType().strip().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.containsKey(mime)) {
            throw invalid("Ảnh đại diện chỉ hỗ trợ JPEG, PNG hoặc WebP.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidMediaUploadException("Không đọc được ảnh đại diện.", exception);
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw invalid("Ảnh đại diện trống hoặc vượt quá 2 MiB.");
        }
        boolean valid = switch (mime) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                    && bytes[4] == 13 && bytes[5] == 10
                    && bytes[6] == 26 && bytes[7] == 10;
            case "image/webp" -> bytes.length >= 12
                    && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
            default -> false;
        };
        if (!valid) {
            throw invalid("Nội dung tệp không khớp định dạng ảnh khai báo.");
        }
        return new ValidatedMediaUpload(bytes, "avatar." + EXTENSIONS.get(mime),
                mime, MediaResourceType.IMAGE);
    }

    private boolean ascii(byte[] bytes, int start, String expected) {
        for (int i = 0; i < expected.length(); i++) {
            if (bytes[start + i] != expected.charAt(i)) return false;
        }
        return true;
    }

    private InvalidMediaUploadException invalid(String message) {
        return new InvalidMediaUploadException(message);
    }
}