package vn.edu.hcmute.drss.services;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.media.ValidatedMediaUpload;

@Component
public class RequestPayloadFingerprint {
    public String digest(CreateCitizenRequest form, List<ValidatedMediaUpload> files) {
        if (form == null || files == null) {
            throw new IllegalArgumentException("Request form is required");
        }
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            update(hash, String.valueOf(form.getDisasterTypeId()));
            update(hash, form.getUrgency());
            update(hash, form.getAddress());
            update(hash, form.getLatitude() == null ? null
                    : form.getLatitude().stripTrailingZeros().toPlainString());
            update(hash, form.getLongitude() == null ? null
                    : form.getLongitude().stripTrailingZeros().toPlainString());
            update(hash, form.getDescription());
            hash.update(ByteBuffer.allocate(4).putInt(files.size()).array());
            for (ValidatedMediaUpload file : files) {
                update(hash, file.originalFilename());
                update(hash, file.mimeType());
                update(hash, file.resourceType().name());
                byte[] bytes = file.content();
                hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                hash.update(bytes);
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private void update(MessageDigest hash, String text) {
        if (text == null) {
            hash.update(ByteBuffer.allocate(4).putInt(-1).array());
        } else {
            byte[] value = text.getBytes(StandardCharsets.UTF_8);
            hash.update(ByteBuffer.allocate(4).putInt(value.length).array());
            hash.update(value);
        }
    }
}
