package vn.edu.hcmute.drss.media;

import java.io.IOException;
import java.util.Map;

public interface CloudinaryGateway {

    Map<String, Object> upload(
            byte[] content,
            Map<String, Object> options
    ) throws IOException;

    Map<String, Object> destroy(
            String publicId,
            Map<String, Object> options
    ) throws IOException;
}