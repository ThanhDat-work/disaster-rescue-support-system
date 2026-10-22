package vn.edu.hcmute.drss.controllers;

import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.drss.dtos.CoordinatorAssignmentRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorCloseRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestDetail;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestFilter;
import vn.edu.hcmute.drss.dtos.CoordinatorTrackingFilter;
import vn.edu.hcmute.drss.dtos.CoordinatorTrackingItem;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.CoordinatorAssignmentService;
import vn.edu.hcmute.drss.services.CoordinatorCloseService;
import vn.edu.hcmute.drss.services.CoordinatorRequestCommandService;
import vn.edu.hcmute.drss.services.CoordinatorRequestQueryService;
import vn.edu.hcmute.drss.services.CoordinatorTrackingService;

@Controller
@RequestMapping("/coordinator")
public class CoordinatorRequestController {

    private final CoordinatorRequestQueryService queryService;
    private final CoordinatorRequestCommandService commandService;
    private final CoordinatorAssignmentService assignmentService;
    private final CoordinatorTrackingService trackingService;
    private final CoordinatorCloseService closeService;

    public CoordinatorRequestController(
            CoordinatorRequestQueryService queryService,
            CoordinatorRequestCommandService commandService,
            CoordinatorAssignmentService assignmentService,
            CoordinatorTrackingService trackingService,
            CoordinatorCloseService closeService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.assignmentService = assignmentService;
        this.trackingService = trackingService;
        this.closeService = closeService;
    }

    @GetMapping("/pending")
    public String pending(
            @ModelAttribute("filter") CoordinatorRequestFilter filter,
            @RequestParam(defaultValue = "0") int page,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Page<RescueRequest> pageData = queryService.findPending(
                    filter,
                    page
            );
            model.addAttribute("pageData", pageData);
            model.addAttribute(
                    "disasterTypes",
                    queryService.getActiveDisasterTypes()
            );
            return "pages/coordinator/pending-list";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return "redirect:/coordinator/pending";
        }
    }

    @GetMapping("/tracking")
    public String tracking(
            @ModelAttribute("filter") CoordinatorTrackingFilter filter,
            @RequestParam(defaultValue = "0") int page,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            Page<CoordinatorTrackingItem> pageData =
                    trackingService.findTracking(filter, page);
            model.addAttribute("pageData", pageData);
            model.addAttribute(
                    "disasterTypes",
                    trackingService.getActiveDisasterTypes()
            );
            model.addAttribute("teams", trackingService.getTeams());
            return "pages/coordinator/tracking-list";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return "redirect:/coordinator/tracking";
        }
    }

    @GetMapping("/requests/{requestId}")
    public String detail(
            @PathVariable UUID requestId,
            Model model
    ) {
        CoordinatorRequestDetail detail = queryService.getDetail(requestId);

        CoordinatorDecisionRequest decisionRequest =
                new CoordinatorDecisionRequest();
        decisionRequest.setVersion(detail.request().getVersion());

        CoordinatorAssignmentRequest assignmentRequest =
                new CoordinatorAssignmentRequest();
        assignmentRequest.setVersion(detail.request().getVersion());

        CoordinatorCloseRequest closeRequest = new CoordinatorCloseRequest();
        closeRequest.setVersion(detail.request().getVersion());

        model.addAttribute("detail", detail);
        model.addAttribute("decisionRequest", decisionRequest);
        model.addAttribute("assignmentRequest", assignmentRequest);
        model.addAttribute("closeRequest", closeRequest);
        model.addAttribute(
                "currentAssignment",
                trackingService.findLatestAssignment(requestId).orElse(null)
        );
        model.addAttribute(
                "availableTeams",
                "VERIFIED".equals(detail.request().getStatus())
                        ? assignmentService.findAvailableTeams()
                        : List.of()
        );
        return "pages/coordinator/request-detail";
    }

    @PostMapping("/requests/{requestId}/decision")
    public String decide(
            @PathVariable UUID requestId,
            @Valid @ModelAttribute("decisionRequest")
            CoordinatorDecisionRequest decisionRequest,
            BindingResult bindingResult,
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    firstValidationMessage(bindingResult)
            );
            return redirectToDetail(requestId);
        }

        try {
            String newStatus = commandService.decide(
                    requestId,
                    requireCoordinatorId(principal),
                    decisionRequest
            );
            redirectAttributes.addFlashAttribute(
                    "successMsg",
                    "VERIFIED".equals(newStatus)
                            ? "Đã xác minh yêu cầu cứu nạn."
                            : "Đã từ chối yêu cầu cứu nạn."
            );
            return redirectToDetail(requestId);
        } catch (CoordinatorRequestNotFoundException exception) {
            return missingRequest(
                    redirectAttributes,
                    exception,
                    "/coordinator/pending"
            );
        } catch (OptimisticLockingFailureException exception) {
            return concurrentUpdate(requestId, redirectAttributes);
        } catch (
                CoordinatorRequestConflictException
                | IllegalArgumentException
                | IllegalStateException exception
        ) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return redirectToDetail(requestId);
        }
    }

    @PostMapping("/requests/{requestId}/assign")
    public String assign(
            @PathVariable UUID requestId,
            @Valid @ModelAttribute("assignmentRequest")
            CoordinatorAssignmentRequest assignmentRequest,
            BindingResult bindingResult,
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    firstValidationMessage(bindingResult)
            );
            return redirectToDetail(requestId);
        }

        try {
            RescueAssignment assignment = assignmentService.assign(
                    requestId,
                    requireCoordinatorId(principal),
                    assignmentRequest
            );
            redirectAttributes.addFlashAttribute(
                    "successMsg",
                    "Đã phân công nhiệm vụ "
                            + assignment.getDisplayCode()
                            + " cho đội cứu hộ."
            );
            return redirectToDetail(requestId);
        } catch (CoordinatorRequestNotFoundException exception) {
            return missingRequest(
                    redirectAttributes,
                    exception,
                    "/coordinator/pending"
            );
        } catch (OptimisticLockingFailureException exception) {
            return concurrentUpdate(requestId, redirectAttributes);
        } catch (
                CoordinatorRequestConflictException
                | IllegalArgumentException
                | IllegalStateException exception
        ) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return redirectToDetail(requestId);
        }
    }

    @PostMapping("/requests/{requestId}/close")
    public String close(
            @PathVariable UUID requestId,
            @Valid @ModelAttribute("closeRequest")
            CoordinatorCloseRequest closeRequest,
            BindingResult bindingResult,
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    firstValidationMessage(bindingResult)
            );
            return redirectToDetail(requestId);
        }

        try {
            closeService.close(
                    requestId,
                    requireCoordinatorId(principal),
                    closeRequest
            );
            redirectAttributes.addFlashAttribute(
                    "successMsg",
                    "Đã đóng yêu cầu cứu nạn."
            );
            return redirectToDetail(requestId);
        } catch (CoordinatorRequestNotFoundException exception) {
            return missingRequest(
                    redirectAttributes,
                    exception,
                    "/coordinator/tracking"
            );
        } catch (OptimisticLockingFailureException exception) {
            return concurrentUpdate(requestId, redirectAttributes);
        } catch (
                CoordinatorRequestConflictException
                | IllegalArgumentException
                | IllegalStateException exception
        ) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return redirectToDetail(requestId);
        }
    }

    private String missingRequest(
            RedirectAttributes redirectAttributes,
            CoordinatorRequestNotFoundException exception,
            String redirectPath
    ) {
        redirectAttributes.addFlashAttribute(
                "errorMsg",
                exception.getMessage()
        );
        return "redirect:" + redirectPath;
    }

    private String concurrentUpdate(
            UUID requestId,
            RedirectAttributes redirectAttributes
    ) {
        redirectAttributes.addFlashAttribute(
                "errorMsg",
                "Yêu cầu vừa được cập nhật. Vui lòng tải lại trang."
        );
        return redirectToDetail(requestId);
    }

    private UUID requireCoordinatorId(
            AuthenticatedUserPrincipal principal
    ) {
        if (principal == null || principal.userId() == null) {
            throw new IllegalStateException(
                    "Không xác định được điều phối viên đang đăng nhập."
            );
        }
        return principal.userId();
    }

    private String firstValidationMessage(BindingResult bindingResult) {
        String message = bindingResult.getAllErrors()
                .get(0)
                .getDefaultMessage();
        return message == null || message.isBlank()
                ? "Dữ liệu xử lý không hợp lệ."
                : message;
    }

    private String redirectToDetail(UUID requestId) {
        return "redirect:/coordinator/requests/" + requestId;
    }
}