package vn.edu.hcmute.drss.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmute.drss.dtos.NotificationInbox;
import vn.edu.hcmute.drss.dtos.NotificationItem;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.NotificationAccessService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationAccessService notificationAccess;

    public NotificationController(
            NotificationAccessService notificationAccess
    ) {
        this.notificationAccess = notificationAccess;
    }

    @GetMapping
    public NotificationInbox loadInbox(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal
    ) {
        return notificationAccess.loadInbox(principal.userId());
    }

    @PostMapping("/{notificationId}/read")
    public NotificationItem markRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal
    ) {
        return notificationAccess.markRead(
                notificationId,
                principal.userId()
        );
    }

    @PostMapping("/read-all")
    public int markAllRead(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal
    ) {
        return notificationAccess.markAllRead(principal.userId());
    }

    @PostMapping("/read-selected")
    public int markSelectedRead(
            @RequestBody List<UUID> notificationIds,
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal
    ) {
        return notificationAccess.markSelectedRead(
                notificationIds,
                principal.userId()
        );
    }
}
