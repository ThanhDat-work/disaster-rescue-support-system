package vn.edu.hcmute.drss.controllers;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.RescuerService;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import vn.edu.hcmute.drss.dtos.CompleteRescueRequest;
import vn.edu.hcmute.drss.dtos.StartRescueRequest;

@Controller
@RequestMapping("/team")
public class RescuerController {

    private final RescuerService rescuerService;

    public RescuerController(RescuerService rescuerService) {
        this.rescuerService = rescuerService;
    }

    @GetMapping("/tasks")
    public String listTasks(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            Model model
    ) {
        List<RescueAssignment> assignments =
                rescuerService.getAssignmentsForUser(
                        requirePrincipal(principal).userId()
                );

        model.addAttribute("assignments", assignments);
        return "pages/rescuer/task-list";
    }

    @GetMapping("/tasks/{assignmentId}")
    public String taskDetail(
            @PathVariable UUID assignmentId,
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            RescueAssignment assignment =
                    rescuerService.getAssignmentDetail(
                            assignmentId,
                            requirePrincipal(principal).userId()
                    );

            model.addAttribute("assignment", assignment);
            return "pages/rescuer/task-detail";
        } catch (IllegalArgumentException | SecurityException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return "redirect:/team/tasks";
        }
    }

   @PostMapping("/tasks/{assignmentId}/start")
public String startRescue(
        @PathVariable UUID assignmentId,
        @Valid @ModelAttribute("startRescueRequest")
        StartRescueRequest form,
        BindingResult bindingResult,
        @AuthenticationPrincipal
        AuthenticatedUserPrincipal principal,
        RedirectAttributes redirectAttributes
) {
    if (bindingResult.hasErrors()) {
        addValidationMessage(
                bindingResult,
                redirectAttributes
        );
        return redirectToDetail(assignmentId);
    }

    try {
        rescuerService.startRescue(
                assignmentId,
                requirePrincipal(principal).userId(),
                form.notes()
        );

        redirectAttributes.addFlashAttribute(
                "successMsg",
                "Đã bắt đầu thực hiện nhiệm vụ cứu hộ."
        );
    } catch (OptimisticLockingFailureException exception) {
        addConcurrentUpdateMessage(redirectAttributes);
    } catch (
            IllegalArgumentException
            | IllegalStateException
            | SecurityException exception
    ) {
        redirectAttributes.addFlashAttribute(
                "errorMsg",
                exception.getMessage()
        );
    }

    return redirectToDetail(assignmentId);
}


   @PostMapping("/tasks/{assignmentId}/complete")
public String completeRescue(
        @PathVariable UUID assignmentId,
        @Valid @ModelAttribute("completeRescueRequest")
        CompleteRescueRequest form,
        BindingResult bindingResult,
        @AuthenticationPrincipal
        AuthenticatedUserPrincipal principal,
        RedirectAttributes redirectAttributes
) {
    if (bindingResult.hasErrors()) {
        addValidationMessage(
                bindingResult,
                redirectAttributes
        );
        return redirectToDetail(assignmentId);
    }

    try {
        rescuerService.completeRescue(
                assignmentId,
                requirePrincipal(principal).userId(),
                form.notes()
        );

        redirectAttributes.addFlashAttribute(
                "successMsg",
                "Đã hoàn tất nhiệm vụ cứu hộ."
        );
    } catch (OptimisticLockingFailureException exception) {
        addConcurrentUpdateMessage(redirectAttributes);
    } catch (
            IllegalArgumentException
            | IllegalStateException
            | SecurityException exception
    ) {
        redirectAttributes.addFlashAttribute(
                "errorMsg",
                exception.getMessage()
        );
    }

    return redirectToDetail(assignmentId);
}

    private AuthenticatedUserPrincipal requirePrincipal(
            AuthenticatedUserPrincipal principal
    ) {
        if (principal == null) {
            throw new IllegalStateException(
                    "Không tìm thấy thông tin người dùng đã xác thực."
            );
        }

        return principal;
    }

    private void addConcurrentUpdateMessage(
            RedirectAttributes redirectAttributes
    ) {
        redirectAttributes.addFlashAttribute(
                "errorMsg",
                "Nhiệm vụ vừa được cập nhật bởi một thao tác khác. "
                        + "Vui lòng tải lại trang và kiểm tra trạng thái mới."
        );
    }

    private void addValidationMessage(
        BindingResult bindingResult,
        RedirectAttributes redirectAttributes
) {
    String message = bindingResult
            .getAllErrors()
            .stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("Dữ liệu gửi lên không hợp lệ.");

    redirectAttributes.addFlashAttribute(
            "errorMsg",
            message
    );
}
    private String redirectToDetail(UUID assignmentId) {
        return "redirect:/team/tasks/" + assignmentId;
    }
}
