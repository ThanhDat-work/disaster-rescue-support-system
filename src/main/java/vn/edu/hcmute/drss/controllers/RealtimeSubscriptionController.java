package vn.edu.hcmute.drss.controllers;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmute.drss.security.AuthenticatedUserPrincipal;
import vn.edu.hcmute.drss.services.RealtimeSubscriptionService;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeSubscriptionController {

    private final RealtimeSubscriptionService subscriptions;

    public RealtimeSubscriptionController(RealtimeSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping("/subscriptions")
    public SubscriptionView subscriptions(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return new SubscriptionView(subscriptions.eligibleTeamIds(
                principal.userId(), principal.activeRole()));
    }

    public record SubscriptionView(List<UUID> teamIds) {
        public SubscriptionView {
            teamIds = List.copyOf(teamIds);
        }
    }
}
