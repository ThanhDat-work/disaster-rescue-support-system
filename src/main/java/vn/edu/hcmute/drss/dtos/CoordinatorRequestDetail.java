package vn.edu.hcmute.drss.dtos;

import java.util.List;
import vn.edu.hcmute.drss.entities.MediaAsset;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.StatusHistory;

public record CoordinatorRequestDetail(
        RescueRequest request,
        List<StatusHistory> history,
        List<MediaAsset> media
) {
    public CoordinatorRequestDetail {
        history = List.copyOf(history);
        media = List.copyOf(media);
    }
}
