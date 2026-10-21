package vn.edu.hcmute.drss.media;

import vn.edu.hcmute.drss.entities.MediaResourceType;

public record ValidatedMediaUpload(
        byte[] content,
        String originalFilename,
        String mimeType,
        MediaResourceType resourceType
) {

    public ValidatedMediaUpload {
        content = content == null ? null : content.clone();
    }

    @Override
    public byte[] content() {
        return content == null ? null : content.clone();
    }
}