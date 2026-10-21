package vn.edu.hcmute.drss.media;

import com.cloudinary.Cloudinary;

import java.io.IOException;
import java.util.Map;

public final class CloudinarySdkGateway implements CloudinaryGateway {

    private final Cloudinary cloudinary;

    public CloudinarySdkGateway(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> upload(
            byte[] content,
            Map<String, Object> options
    ) throws IOException {
        return (Map<String, Object>) cloudinary.uploader()
                .upload(content, options);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> destroy(
            String publicId,
            Map<String, Object> options
    ) throws IOException {
        return (Map<String, Object>) cloudinary.uploader()
                .destroy(publicId, options);
    }
}