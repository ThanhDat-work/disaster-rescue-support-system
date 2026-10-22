package vn.edu.hcmute.drss.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.media")
public record MediaStorageProperties(
        String cloudName,
        String apiKey,
        String apiSecret,
        String folder,
        DataSize maxFileSize,
        int maxFilesPerRequest,
        int maxVideoFilesPerRequest
) {

    public MediaStorageProperties {
        cloudName = requireText(cloudName, "Cloudinary cloud name");
        apiKey = requireText(apiKey, "Cloudinary API key");
        apiSecret = requireText(apiSecret, "Cloudinary API secret");
        folder = normalizeFolder(folder);

        if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException(
                    "Media maximum file size must be greater than zero"
            );
        }

        if (maxFilesPerRequest <= 0) {
            throw new IllegalArgumentException(
                    "Media maximum file count must be greater than zero"
            );
        }

        if (maxVideoFilesPerRequest < 0
                || maxVideoFilesPerRequest > maxFilesPerRequest) {
            throw new IllegalArgumentException(
                    "Media maximum video count is invalid"
            );
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeFolder(String value) {
        String normalized = requireText(value, "Cloudinary folder")
                .replace('\\', '/');

        if (normalized.startsWith("/")
                || normalized.endsWith("/")
                || normalized.contains("//")
                || normalized.contains("..")) {
            throw new IllegalArgumentException(
                    "Cloudinary folder must be a safe relative path"
            );
        }

        if (!normalized.matches("[A-Za-z0-9/_-]+")) {
            throw new IllegalArgumentException(
                    "Cloudinary folder contains unsupported characters"
            );
        }

        return normalized;
    }
}