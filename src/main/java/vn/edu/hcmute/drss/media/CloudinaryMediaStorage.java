package vn.edu.hcmute.drss.media;

import vn.edu.hcmute.drss.configs.MediaStorageProperties;
import vn.edu.hcmute.drss.entities.MediaResourceType;
import vn.edu.hcmute.drss.exceptions.MediaStorageException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CloudinaryMediaStorage implements MediaStorage {

    private final CloudinaryGateway gateway;
    private final MediaStorageProperties properties;

    public CloudinaryMediaStorage(
            CloudinaryGateway gateway,
            MediaStorageProperties properties
    ) {
        this.gateway = gateway;
        this.properties = properties;
    }

    @Override
    public UploadedMedia upload(ValidatedMediaUpload upload) {
        if (upload == null
                || upload.content() == null
                || upload.content().length == 0
                || upload.resourceType() == null) {
            throw new IllegalArgumentException(
                    "Validated media upload is required"
            );
        }

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("folder", properties.folder());
        options.put(
                "resource_type",
                upload.resourceType().cloudinaryValue()
        );
        options.put("overwrite", false);
        options.put("unique_filename", true);
        options.put("use_filename", false);

        try {
            Map<String, Object> response = gateway.upload(
                    upload.content(),
                    options
            );
            return mapResponse(upload, response);
        } catch (IOException exception) {
            throw new MediaStorageException(
                    "Cloudinary upload failed",
                    exception
            );
        }
    }

    @Override
    public void delete(
            String publicId,
            MediaResourceType resourceType
    ) {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException(
                    "Cloudinary public ID is required"
            );
        }
        if (resourceType == null) {
            throw new IllegalArgumentException(
                    "Media resource type is required"
            );
        }

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("resource_type", resourceType.cloudinaryValue());
        options.put("invalidate", true);

        try {
            Map<String, Object> response = gateway.destroy(
                    publicId.trim(),
                    options
            );
            String result = optionalText(response, "result")
                    .toLowerCase(Locale.ROOT);

            if (!"ok".equals(result) && !"not found".equals(result)) {
                throw new MediaStorageException(
                        "Cloudinary delete returned an unexpected result"
                );
            }
        } catch (IOException exception) {
            throw new MediaStorageException(
                    "Cloudinary delete failed",
                    exception
            );
        }
    }

    private UploadedMedia mapResponse(
            ValidatedMediaUpload upload,
            Map<String, Object> response
    ) {
        if (response == null) {
            throw new MediaStorageException(
                    "Cloudinary upload returned no response"
            );
        }

        String publicId = requiredText(response, "public_id");
        String secureUrl = requiredText(response, "secure_url");
        String format = requiredText(response, "format")
                .toLowerCase(Locale.ROOT);
        String cloudinaryType = requiredText(
                response,
                "resource_type"
        ).toLowerCase(Locale.ROOT);

        if (!secureUrl.startsWith("https://")) {
            throw new MediaStorageException(
                    "Cloudinary response did not contain a secure URL"
            );
        }

        if (!upload.resourceType().cloudinaryValue()
                .equals(cloudinaryType)) {
            throw new MediaStorageException(
                    "Cloudinary response resource type does not match upload"
            );
        }

        Integer width = optionalInteger(response, "width");
        Integer height = optionalInteger(response, "height");
        int bytes = requiredInteger(response, "bytes");
        BigDecimal duration = optionalDecimal(response, "duration");

        return new UploadedMedia(
                publicId,
                secureUrl,
                upload.resourceType(),
                upload.mimeType(),
                format,
                width,
                height,
                duration,
                bytes
        );
    }

    private String requiredText(
            Map<String, Object> response,
            String key
    ) {
        String value = optionalText(response, key);
        if (value.isBlank()) {
            throw new MediaStorageException(
                    "Cloudinary response is missing " + key
            );
        }
        return value;
    }

    private String optionalText(
            Map<String, Object> response,
            String key
    ) {
        Object value = response == null ? null : response.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private int requiredInteger(
            Map<String, Object> response,
            String key
    ) {
        Integer value = optionalInteger(response, key);
        if (value == null || value < 0) {
            throw new MediaStorageException(
                    "Cloudinary response has invalid " + key
            );
        }
        return value;
    }

    private Integer optionalInteger(
            Map<String, Object> response,
            String key
    ) {
        Object value = response.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long converted = number.longValue();
            if (converted < 0 || converted > Integer.MAX_VALUE) {
                throw new MediaStorageException(
                        "Cloudinary response has invalid " + key
                );
            }
            return (int) converted;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            throw new MediaStorageException(
                    "Cloudinary response has invalid " + key,
                    exception
            );
        }
    }

    private BigDecimal optionalDecimal(
            Map<String, Object> response,
            String key
    ) {
        Object value = response.get(key);
        if (value == null) {
            return null;
        }
        try {
            BigDecimal decimal = new BigDecimal(value.toString());
            if (decimal.signum() < 0) {
                throw new NumberFormatException("negative value");
            }
            return decimal;
        } catch (NumberFormatException exception) {
            throw new MediaStorageException(
                    "Cloudinary response has invalid " + key,
                    exception
            );
        }
    }
}