package vn.edu.hcmute.drss.services;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.media.MediaUploadValidator;

@Service
public class CitizenCreateWorkflowService {
    private final MediaUploadValidator validator;
    private final RequestPayloadFingerprint fingerprint;
    private final RequestCreationGateService gate;
    private final CitizenRequestMediaService media;

    public CitizenCreateWorkflowService(MediaUploadValidator validator,
            RequestPayloadFingerprint fingerprint, RequestCreationGateService gate,
            CitizenRequestMediaService media) {
        this.validator = validator;
        this.fingerprint = fingerprint;
        this.gate = gate;
        this.media = media;
    }

    public RequestCreationDecision create(UUID userId, UUID key,
            CreateCitizenRequest form, List<MultipartFile> files) {
        // Validate and hash bytes before admission, but never upload on replay.
        var validated = validator.validate(files);
        String digest = fingerprint.digest(form, validated);
        RequestCreationDecision decision = gate.claim(userId, key, digest);
        if (decision.kind() != RequestCreationDecision.Kind.NEW) return decision;
        try {
            var result = media.createAdmitted(userId, key, form, validated);
            return new RequestCreationDecision(
                    RequestCreationDecision.Kind.NEW, result.getId(), null);
        } catch (RuntimeException failure) {
            try {
                gate.fail(userId, key);
            } catch (RuntimeException markFailure) {
                failure.addSuppressed(markFailure);
            }
            throw failure;
        }
    }
}
