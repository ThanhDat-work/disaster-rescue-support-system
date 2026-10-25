package vn.edu.hcmute.drss.controllers;

import jakarta.validation.Valid;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.exceptions.MediaStorageException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.CitizenCreateWorkflowService;
import vn.edu.hcmute.drss.services.RequestCreationDecision;
import vn.edu.hcmute.drss.services.RequestCreationGateService;
import vn.edu.hcmute.drss.services.RequestCreationStatus;

@RestController
@RequestMapping("/api/request-creations")
public class RequestCreationApiController {
    private final CitizenCreateWorkflowService workflow;
    private final RequestCreationGateService gate;

    public RequestCreationApiController(CitizenCreateWorkflowService workflow,
                                        RequestCreationGateService gate) {
        this.workflow = workflow;
        this.gate = gate;
    }

    @GetMapping(path = "/{key}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RequestCreationStatus> status(
            @PathVariable UUID key,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        // The owner is NEVER accepted from a request parameter or JWT claim.
        return ResponseEntity.ok(gate.status(principal.userId(), key));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Submission> create(
            @Valid @ModelAttribute CreateCitizenRequest form,
            BindingResult binding,
            @RequestParam(name = "mediaFiles", required = false)
            List<MultipartFile> mediaFiles,
            @RequestParam(name = "operationKey", required = false) UUID key,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        if (binding.hasErrors() || key == null) {
            return ResponseEntity.badRequest().body(new Submission(
                    "VALIDATION_ERROR", null, "Kiểm tra lại biểu mẫu và mã thao tác.",
                    false, 0));
        }
        try {
            RequestCreationDecision result = workflow.create(principal.userId(),
                    key, form, normalize(mediaFiles));
            return switch (result.kind()) {
                case NEW, REPLAY -> ResponseEntity.status(
                        result.kind() == RequestCreationDecision.Kind.NEW
                                ? HttpStatus.CREATED : HttpStatus.OK)
                        .body(new Submission("SUCCEEDED", result.requestId(),
                                "Yêu cầu đã được ghi nhận.", false, 0));
                case PROCESSING -> ResponseEntity.accepted().body(new Submission(
                        "PROCESSING", null, "Yêu cầu vẫn đang được xử lý.", false, 0));
                case UNKNOWN -> ResponseEntity.accepted().body(new Submission(
                        "UNKNOWN", null, "Chưa xác định được kết quả gửi.", false, 0));
                case FAILED -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new Submission("FAILED", null,
                                "Không thể thử lại thao tác này. Mở biểu mẫu mới sau khi kiểm tra danh sách yêu cầu.",
                                false, 0));
                case LIMITED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                        new Submission("LIMITED", null,
                                "Bạn đã đạt giới hạn tạo yêu cầu.", false,
                                Math.max(1, Duration.between(LocalDateTime.now(),
                                        result.retryAt()).toSeconds())));
            };
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new Submission(
                    "VALIDATION_ERROR", null, exception.getMessage(), false, 0));
        } catch (MediaStorageException exception) {
            return failedOrUnknown(principal.userId(), key);
        } catch (RuntimeException exception) {
            // Never claim failure if persistence/cleanup status cannot be confirmed.
            return failedOrUnknown(principal.userId(), key);
        }
    }

    private ResponseEntity<Submission> failedOrUnknown(UUID userId, UUID key) {
        try {
            RequestCreationStatus current = gate.status(userId, key);
            if ("FAILED".equals(current.state())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(new Submission("FAILED", null,
                                "Lần gửi trước thất bại. Có thể thử lại với cùng mã thao tác và nội dung.",
                                current.retryAllowed(), 0));
            }
        } catch (RuntimeException ignored) {
            // Status lookup failure is also an unknown outcome to the client.
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                new Submission("UNKNOWN", null,
                        "Chưa xác định được kết quả. Hãy kiểm tra lại bằng mã thao tác này.",
                        false, 0));
    }

    private List<MultipartFile> normalize(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream()
                .filter(Objects::nonNull)
                .filter(file -> !(file.isEmpty()
                        && (file.getOriginalFilename() == null
                        || file.getOriginalFilename().isBlank())))
                .toList();
    }

    public record Submission(String state, UUID requestId, String message,
                             boolean retryAllowed, long retryAfterSeconds) {
    }
}
