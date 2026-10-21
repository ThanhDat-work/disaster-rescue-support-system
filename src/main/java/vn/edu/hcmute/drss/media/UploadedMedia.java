package vn.edu.hcmute.drss.media;

import vn.edu.hcmute.drss.entities.MediaResourceType;

import java.math.BigDecimal;

public record UploadedMedia(
        String publicId,
        String secureUrl,
        MediaResourceType resourceType,
        String mimeType,
        String format,
        Integer width,
        Integer height,
        BigDecimal durationSeconds,
        int bytes
) {
}