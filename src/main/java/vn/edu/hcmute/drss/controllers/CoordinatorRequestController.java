package vn.edu.hcmute.drss.controllers;

import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
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
import vn.edu.hcmute.drss.dtos.CoordinatorDecisionRequest;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestDetail;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestFilter;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestConflictException;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.CoordinatorRequestCommandService;
import vn.edu.hcmute.drss.services.CoordinatorRequestQueryService;
import vn.edu.hcmute.drss.dtos.CoordinatorAssignmentRequest;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.services.CoordinatorAssignmentService;

@Controller
@RequestMapping("/coordinator")
public class CoordinatorRequestController {

    private final CoordinatorRequestQueryService queryService;
    private final CoordinatorRequestCommandService commandService;
    private final CoordinatorAssignmentService assignmentService;

    public CoordinatorRequestController(
            CoordinatorRequestQueryService queryService,
            CoordinatorRequestCommandService commandService,
            CoordinatorAssignmentService assignmentService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.assignmentService = assignmentService;
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

        model.addAttribute("detail", detail);
        model.addAttribute("decisionRequest", decisionRequest);
        model.addAttribute("assignmentRequest", assignmentRequest);
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

            String successMessage = "VERIFIED".equals(newStatus)
                    ? "Đã xác minh yêu cầu cứu nạn."
                    : "Đã từ chối yêu cầu cứu nạn.";

            redirectAttributes.addFlashAttribute(
                    "successMsg",
                    successMessage
            );
            return redirectToDetail(requestId);
        } catch (CoordinatorRequestNotFoundException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return "redirect:/coordinator/pending";
        } catch (OptimisticLockingFailureException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "Yêu cầu vừa được cập nhật. Vui lòng tải lại trang."
            );
            return redirectToDetail(requestId);
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
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return "redirect:/coordinator/pending";
        } catch (OptimisticLockingFailureException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    "Yêu cầu vừa được cập nhật. Vui lòng tải lại trang."
            );
            return redirectToDetail(requestId);
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