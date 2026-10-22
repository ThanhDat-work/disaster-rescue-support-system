package vn.edu.hcmute.drss.controllers;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.drss.dtos.ProfileEditRequest;
import vn.edu.hcmute.drss.dtos.ProfileView;
import vn.edu.hcmute.drss.exceptions.InvalidMediaUploadException;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.AvatarService;
import vn.edu.hcmute.drss.services.ProfileService;
import vn.edu.hcmute.drss.services.ProfileStatisticsService;

@Controller
public class ProfileController {
    private final ProfileService profiles;
    private final ProfileStatisticsService statistics;
    private final AvatarService avatars;

    public ProfileController(ProfileService profiles,
                             ProfileStatisticsService statistics,
                             AvatarService avatars) {
        this.profiles = profiles;
        this.statistics = statistics;
        this.avatars = avatars;
    }

    @InitBinder("profileEdit")
    public void whitelistProfileFields(WebDataBinder binder) {
        binder.setAllowedFields("fullName", "phoneNumber");
    }

    @GetMapping("/account/profile")
    public String show(Authentication authentication, Model model) {
        AuthenticatedUserPrincipal principal = principal(authentication);
        ProfileView profile = profiles.view(principal.userId(), principal.activeRole());
        model.addAttribute("profile", profile);
        model.addAttribute("statistics", statistics.forRole(principal.userId(),
                principal.activeRole(), profile.createdAt()));
        return "pages/account/profile";
    }

    @GetMapping("/account/profile/edit")
    public String editForm(Authentication authentication, Model model) {
        ProfileView profile = profiles.view(principal(authentication).userId(),
                principal(authentication).activeRole());
        model.addAttribute("profile", profile);
        if (!model.containsAttribute("profileEdit")) {
            model.addAttribute("profileEdit", new ProfileEditRequest(
                    profile.fullName(), profile.phoneNumber()));
        }
        return "pages/account/profile-edit";
    }

    @PostMapping("/account/profile")
    public String update(@Valid @ModelAttribute("profileEdit") ProfileEditRequest form,
                         BindingResult errors, Authentication authentication,
                         Model model, RedirectAttributes redirect) {
        AuthenticatedUserPrincipal principal = principal(authentication);
        if (errors.hasErrors()) {
            model.addAttribute("profile", profiles.view(principal.userId(),
                    principal.activeRole()));
            return "pages/account/profile-edit";
        }
        profiles.update(principal.userId(), form);
        redirect.addFlashAttribute("success", "Đã cập nhật hồ sơ.");
        return "redirect:/account/profile";
    }

    @PostMapping("/account/profile/avatar")
    public String uploadAvatar(@RequestParam("avatar") MultipartFile avatar,
                               Authentication authentication,
                               RedirectAttributes redirect) {
        try {
            avatars.replace(principal(authentication).userId(), avatar);
            redirect.addFlashAttribute("success", "Đã cập nhật ảnh đại diện.");
        } catch (InvalidMediaUploadException invalid) {
            redirect.addFlashAttribute("error", invalid.getMessage());
        } catch (RuntimeException failed) {
            redirect.addFlashAttribute("error", "Không thể cập nhật ảnh đại diện. Vui lòng thử lại.");
        }
        return "redirect:/account/profile";
    }

    @PostMapping("/account/profile/avatar/delete")
    public String deleteAvatar(Authentication authentication,
                               RedirectAttributes redirect) {
        try {
            avatars.remove(principal(authentication).userId());
            redirect.addFlashAttribute("success", "Đã xóa ảnh đại diện.");
        } catch (RuntimeException failed) {
            redirect.addFlashAttribute("error", "Không thể xóa ảnh đại diện. Vui lòng thử lại.");
        }
        return "redirect:/account/profile";
    }

    private AuthenticatedUserPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUserPrincipal user)) {
            throw new IllegalStateException("Authenticated user is required");
        }
        return user;
    }
}