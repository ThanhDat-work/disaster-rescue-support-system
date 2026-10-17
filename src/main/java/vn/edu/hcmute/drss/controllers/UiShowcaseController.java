package vn.edu.hcmute.drss.controllers;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Profile("dev")
@RequestMapping("/dev/ui-showcase")
public class UiShowcaseController {
    @GetMapping
    public String showcase(@RequestParam(defaultValue = "1") int page, Model model) {
        var rows = List.of("REQ-0001", "REQ-0002", "REQ-0003", "REQ-0004", "REQ-0005");
        int safePage = Math.max(0, Math.min(page, 2));
        model.addAttribute("pageData", new PageImpl<>(rows.subList(safePage, safePage + 1), PageRequest.of(safePage, 1), 3));
        model.addAttribute("pageUrl", "/dev/ui-showcase");
        model.addAttribute("pageQuery", "?filter=showcase&");
        return "pages/sample-ui";
    }
}
