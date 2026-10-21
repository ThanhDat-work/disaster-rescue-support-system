package vn.edu.hcmute.drss.controllers;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
import vn.edu.hcmute.drss.dtos.AdminDisasterTypeRequest;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.exceptions.AdminDomainException;
import vn.edu.hcmute.drss.services.AdminDisasterTypeService;
import java.util.List;

@Controller
@RequestMapping("/admin/disasters")
public class AdminDisasterTypeController {

    private final AdminDisasterTypeService service;

    public AdminDisasterTypeController(
            AdminDisasterTypeService service
    ) {
        this.service = service;
    }

    @ModelAttribute("pageSizes")
    public List<Integer> pageSizes() {
        return List.of(10, 20, 50);
    }

    @GetMapping
    public String list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model
    ) {
        try {
            Page<DisasterType> result = service.search(
                    keyword,
                    active,
                    page,
                    size
            );

            model.addAttribute("result", result);
        } catch (AdminDomainException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute(
                    "result",
                    service.search(null, null, 0, 20)
            );
            page = 0;
            size = 20;
        }

        model.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        model.addAttribute("active", active);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        return "pages/admin/disaster-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute(
                "form",
                new AdminDisasterTypeRequest("", "", "")
        );
        model.addAttribute("editing", false);
        return "pages/admin/disaster-form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form")
            AdminDisasterTypeRequest form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editing", false);
return "pages/admin/disaster-form";
        }

        try {
            service.create(form);
        } catch (AdminDomainException exception) {
            reject(bindingResult, exception);
            model.addAttribute("editing", false);
            return "pages/admin/disaster-form";
        }

        redirectAttributes.addFlashAttribute(
                "successMsg",
                "Đã tạo loại thiên tai"
        );
        return "redirect:/admin/disasters";
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable long id,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            DisasterType disasterType = service.get(id);
            model.addAttribute(
                    "form",
                    new AdminDisasterTypeRequest(
                            disasterType.getDisasterCode(),
                            disasterType.getDisasterName(),
                            disasterType.getDescription()
                    )
            );
            model.addAttribute("editing", true);
            model.addAttribute("disasterId", id);
            return "pages/admin/disaster-form";
        } catch (AdminDomainException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMsg",
                    exception.getMessage()
            );
            return "redirect:/admin/disasters";
        }
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            @Valid @ModelAttribute("form")
            AdminDisasterTypeRequest form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            prepareEditModel(model, id);
            return "pages/admin/disaster-form";
        }

        try {
            service.update(id, form);
        } catch (AdminDomainException exception) {
            reject(bindingResult, exception);
            prepareEditModel(model, id);
            return "pages/admin/disaster-form";
        }

        redirectAttributes.addFlashAttribute(
                "successMsg",
                "Đã cập nhật loại thiên tai"
        );
        return "redirect:/admin/disasters";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(
            @PathVariable long id,
            @RequestParam boolean active,
            RedirectAttributes redirectAttributes
    ) {
        try {
            service.changeStatus(id, active);
            redirectAttributes.addFlashAttribute(
                    "successMsg",
                    active
                            ? "Đã kích hoạt loại thiên tai"
                            : "Đã vô hiệu hóa loại thiên tai"
            );
        } catch (AdminDomainException exception) {
            redirectAttributes.addFlashAttribute(
"errorMsg",
                    exception.getMessage()
            );
        }

        return "redirect:/admin/disasters";
    }

    private void prepareEditModel(Model model, long id) {
        model.addAttribute("editing", true);
        model.addAttribute("disasterId", id);
    }

    private void reject(
            BindingResult bindingResult,
            AdminDomainException exception
    ) {
        if (exception.getField() != null
                && bindingResult.getFieldError(exception.getField()) == null) {
            bindingResult.rejectValue(
                    exception.getField(),
                    "admin.domain",
                    exception.getMessage()
            );
            return;
        }

        bindingResult.reject(
                "admin.domain",
                exception.getMessage()
        );
    }
}
