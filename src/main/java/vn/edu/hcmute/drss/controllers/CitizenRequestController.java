package vn.edu.hcmute.drss.controllers;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.drss.configs.MediaStorageProperties;
import vn.edu.hcmute.drss.dtos.CancelCitizenRequest;
import vn.edu.hcmute.drss.dtos.CreateCitizenRequest;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.exceptions.MediaStorageException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.CitizenRequestService;
import vn.edu.hcmute.drss.services.CitizenCreateWorkflowService;
import vn.edu.hcmute.drss.services.RequestCreationDecision;

@Controller
@RequestMapping("/requests")
public class CitizenRequestController {

    private final CitizenRequestService service;
    private final MediaStorageProperties mediaProperties;
    private final CitizenCreateWorkflowService workflow;

    public CitizenRequestController(
            CitizenRequestService service,
            MediaStorageProperties mediaProperties,
            CitizenCreateWorkflowService workflow
    ) {
        this.service = service;
        this.mediaProperties = mediaProperties;
        this.workflow = workflow;
    }

    @GetMapping
    public String list(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        Page<RescueRequest> pageData = service.list(
                principal.userId(),
                page
        );
        model.addAttribute("pageData", pageData);
        model.addAttribute("pageUrl", "/requests?");
        model.addAttribute("pageQuery", "");
        return "pages/citizen/request-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("operationKey")) {
            model.addAttribute("operationKey", UUID.randomUUID());
        }
        model.addAttribute(
                "createCitizenRequest",
                new CreateCitizenRequest()
        );
        return showCreateForm(model);
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("createCitizenRequest")
            CreateCitizenRequest form,
            BindingResult binding,
            @RequestParam(name = "mediaFiles", required = false)
            List<MultipartFile> mediaFiles,
            @RequestParam(name = "operationKey", required = false)
            UUID operationKey,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Model model,
            RedirectAttributes flash
    ) {
        if (binding.hasErrors()) {
            model.addAttribute("operationKey", operationKey == null
                    ? UUID.randomUUID() : operationKey);
            return showCreateForm(model);
        }

        if (operationKey == null) {
            flash.addFlashAttribute("errorMsg", "Thiếu mã thao tác. Vui lòng mở lại biểu mẫu.");
            return "redirect:/requests/new";
        }

        try {
            RequestCreationDecision outcome = workflow.create(
                    principal.userId(),
                    operationKey,
                    form,
                    normalizeMediaFiles(mediaFiles)
            );
            if (outcome.requestId() != null) {
                flash.addFlashAttribute("successMsg",
                        outcome.kind() == RequestCreationDecision.Kind.REPLAY
                                ? "Yêu cầu này đã được gửi trước đó."
                                : "Đã gửi yêu cầu.");
                return "redirect:/requests/" + outcome.requestId();
            }
            flash.addFlashAttribute("operationKey",
                    outcome.kind() == RequestCreationDecision.Kind.FAILED
                            ? UUID.randomUUID() : operationKey);
            String reason = switch (outcome.kind()) {
                case LIMITED -> "Bạn đã đạt giới hạn tạo yêu cầu. Vui lòng thử lại sau "
                        + Math.max(1, java.time.Duration.between(
                                java.time.LocalDateTime.now(), outcome.retryAt()
                        ).toSeconds()) + " giây.";
                case PROCESSING -> "Yêu cầu đang được xử lý; đừng gửi một yêu cầu mới.";
                case FAILED -> "Lần gửi trước thất bại. Hãy mở biểu mẫu mới nếu muốn thử lại.";
                case UNKNOWN -> "Chưa xác định được kết quả. Vui lòng kiểm tra danh sách yêu cầu trước khi thử lại.";
                default -> "Không thể xác định kết quả gửi yêu cầu.";
            };
            flash.addFlashAttribute("errorMsg", reason);
            return "redirect:/requests/new";
        } catch (IllegalArgumentException exception) {
            flash.addFlashAttribute("operationKey", UUID.randomUUID());
            flash.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return "redirect:/requests/new";
        } catch (MediaStorageException exception) {
            flash.addFlashAttribute("operationKey", UUID.randomUUID());
            flash.addFlashAttribute(
                    "errorMsg",
                    "Kh\u00f4ng th\u1ec3 l\u01b0u media l\u00fac n\u00e0y. "
                            + "Vui l\u00f2ng th\u1eed l\u1ea1i."
            );
            return "redirect:/requests/new";
        }
    }

    @GetMapping("/{requestId}")
    public String detail(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            Model model,
            RedirectAttributes flash
    ) {
        try {
            model.addAttribute(
                    "request",
                    service.detail(requestId, principal.userId())
            );
            model.addAttribute(
                    "history",
                    service.history(requestId, principal.userId())
            );
            model.addAttribute(
                    "media",
                    service.media(requestId, principal.userId())
            );
            model.addAttribute(
                    "cancelCitizenRequest",
                    new CancelCitizenRequest()
            );
            return "pages/citizen/request-detail";
        } catch (IllegalArgumentException exception) {
            flash.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return "redirect:/requests";
        }
    }

    @PostMapping("/{requestId}/cancel")
    public String cancel(
            @PathVariable UUID requestId,
            @Valid @ModelAttribute("cancelCitizenRequest")
            CancelCitizenRequest form,
            BindingResult binding,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            RedirectAttributes flash
    ) {
        if (binding.hasErrors()) {
            flash.addFlashAttribute(
                    "errorMsg",
                    binding.getAllErrors().get(0).getDefaultMessage()
            );
            return "redirect:/requests/" + requestId;
        }

        try {
            service.cancel(
                    requestId,
                    principal.userId(),
                    form.getReason()
            );
            flash.addFlashAttribute(
                    "successMsg",
                    "\u0110\u00e3 h\u1ee7y y\u00eau c\u1ea7u."
            );
        } catch (OptimisticLockingFailureException exception) {
            flash.addFlashAttribute(
                    "errorMsg",
                    "Y\u00eau c\u1ea7u v\u1eeba \u0111\u01b0\u1ee3c c\u1eadp nh\u1eadt. "
                            + "T\u1ea3i l\u1ea1i trang \u0111\u1ec3 xem tr\u1ea1ng th\u00e1i m\u1edbi."
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            flash.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
        }
        return "redirect:/requests/" + requestId;
    }

    private List<MultipartFile> normalizeMediaFiles(
            List<MultipartFile> files
    ) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        return files.stream()
                .filter(Objects::nonNull)
                .filter(file -> !(
                        file.isEmpty()
                                && (file.getOriginalFilename() == null
                                || file.getOriginalFilename().isBlank())
                ))
                .toList();
    }

    private String showCreateForm(Model model) {
        model.addAttribute(
                "disasterTypes",
                service.getActiveDisasterTypes()
        );
        model.addAttribute(
                "mediaMaxFileSizeBytes",
                mediaProperties.maxFileSize().toBytes()
        );
        model.addAttribute(
                "mediaMaxFiles",
                mediaProperties.maxFilesPerRequest()
        );
        model.addAttribute(
                "mediaMaxVideos",
                mediaProperties.maxVideoFilesPerRequest()
        );
        return "pages/citizen/request-form";
    }
}
