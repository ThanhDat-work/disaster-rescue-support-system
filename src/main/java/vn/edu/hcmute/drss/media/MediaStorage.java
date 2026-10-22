package vn.edu.hcmute.drss.media;

import vn.edu.hcmute.drss.entities.MediaResourceType;

public interface MediaStorage {

    UploadedMedia upload(ValidatedMediaUpload upload);

    void delete(String publicId, MediaResourceType resourceType);
}