package vn.edu.hcmute.drss.dtos;

import java.util.Objects;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;

public record CoordinatorTrackingItem(
        RescueRequest request,
        RescueAssignment assignment
) {
    public CoordinatorTrackingItem {
        Objects.requireNonNull(request, "Request is required");
    }
}