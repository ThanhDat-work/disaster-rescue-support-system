package vn.edu.hcmute.drss.controllers;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Profile("dev")
@RequestMapping("/dev/ui-showcase")
public class UiShowcaseController {
    @GetMapping
    public String showcase(@RequestParam(defaultValue = "0") int page, Model model) {
        var rows = List.of("REQ-0001", "REQ-0002", "REQ-0003", "REQ-0004", "REQ-0005");
        int pageSize = 1;
        int totalPages = (int) Math.ceil((double) rows.size() / pageSize);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int start = safePage * pageSize;
        int end = Math.min(start + pageSize, rows.size());
        model.addAttribute("pageData", new PageImpl<>(rows.subList(start, end), PageRequest.of(safePage, pageSize), rows.size()));
        model.addAttribute("pageUrl", "/dev/ui-showcase");
        model.addAttribute("pageQuery", "?filter=showcase&");
        return "pages/sample-ui";
    }

    @PostMapping("/demo/request")
    public String submitDemoRequest(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("successMsg", "Yêu cầu demo đã được gửi thành công.");
        return "redirect:/dev/ui-showcase";
    }

    @PostMapping("/demo/reject")
    public String confirmDemoReject(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("successMsg", "Thao tác từ chối demo đã được xác nhận.");
        return "redirect:/dev/ui-showcase";
    }

    @PostMapping("/demo/error")
    public String showDemoError(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMsg", "Đây là thông báo lỗi minh họa; không có dữ liệu nào được thay đổi.");
        return "redirect:/dev/ui-showcase";
    }
}
