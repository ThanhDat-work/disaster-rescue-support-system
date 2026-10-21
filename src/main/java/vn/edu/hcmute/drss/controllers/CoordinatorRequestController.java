package vn.edu.hcmute.drss.controllers;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestFilter;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.services.CoordinatorRequestQueryService;

@Controller
@RequestMapping("/coordinator")
public class CoordinatorRequestController {

    private final CoordinatorRequestQueryService queryService;

    public CoordinatorRequestController(
            CoordinatorRequestQueryService queryService
    ) {
        this.queryService = queryService;
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
        model.addAttribute("detail", queryService.getDetail(requestId));
        return "pages/coordinator/request-detail";
    }
}
