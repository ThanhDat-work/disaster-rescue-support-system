package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestDetail;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestFilter;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.repositories.DisasterTypeRepository;
import vn.edu.hcmute.drss.repositories.MediaAssetRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;

@Service
public class CoordinatorRequestQueryService {

    public static final int PAGE_SIZE = 10;
    public static final int MAX_KEYWORD_LENGTH = 150;

    private static final Set<String> URGENCIES = Set.of(
            "LOW", "MEDIUM", "HIGH", "CRITICAL"
    );

    private final RescueRequestRepository requests;
    private final DisasterTypeRepository disasterTypes;
    private final StatusHistoryRepository histories;
    private final MediaAssetRepository mediaAssets;

    public CoordinatorRequestQueryService(
            RescueRequestRepository requests,
            DisasterTypeRepository disasterTypes,
            StatusHistoryRepository histories,
            MediaAssetRepository mediaAssets
    ) {
        this.requests = requests;
        this.disasterTypes = disasterTypes;
        this.histories = histories;
        this.mediaAssets = mediaAssets;
    }

    @Transactional(readOnly = true)
    public Page<RescueRequest> findPending(
            CoordinatorRequestFilter filter,
            int requestedPage
    ) {
        CoordinatorRequestFilter safeFilter = filter == null
                ? new CoordinatorRequestFilter()
                : filter;

        String keyword = normalizeKeyword(safeFilter.getKeyword());
        String urgency = normalizeUrgency(safeFilter.getUrgency());
        validateDateRange(safeFilter);

        LocalDateTime fromInclusive = safeFilter.getFrom() == null
                ? null
                : safeFilter.getFrom().atStartOfDay();
        LocalDateTime toExclusive = safeFilter.getTo() == null
                ? null
                : safeFilter.getTo().plusDays(1).atStartOfDay();

        int pageNumber = Math.max(requestedPage, 0);
        Page<RescueRequest> result = search(
                keyword,
                urgency,
                safeFilter.getDisasterTypeId(),
                fromInclusive,
                toExclusive,
                PageRequest.of(pageNumber, PAGE_SIZE)
        );

        if (result.getTotalPages() > 0
                && pageNumber >= result.getTotalPages()) {
            result = search(
                    keyword,
                    urgency,
                    safeFilter.getDisasterTypeId(),
                    fromInclusive,
                    toExclusive,
                    PageRequest.of(result.getTotalPages() - 1, PAGE_SIZE)
            );
        }

        return result;
    }

    @Transactional(readOnly = true)
    public CoordinatorRequestDetail getDetail(UUID requestId) {
        RescueRequest request = requests.findCoordinatorDetailById(requestId)
                .orElseThrow(CoordinatorRequestNotFoundException::new);

        return new CoordinatorRequestDetail(
                request,
                histories.findByRequest_IdOrderByCreatedAtAscIdAsc(requestId),
                mediaAssets.findByRequest_IdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        requestId
                )
        );
    }

    @Transactional(readOnly = true)
    public List<DisasterType> getActiveDisasterTypes() {
        return disasterTypes.findByIsActiveTrueOrderByDisasterNameAsc();
    }

    private Page<RescueRequest> search(
            String keyword,
            String urgency,
            Long disasterTypeId,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            Pageable pageable
    ) {
        return requests.searchPendingForCoordinator(
                keyword,
                urgency,
                disasterTypeId,
                fromInclusive,
                toExclusive,
                pageable
        );
    }

    private String normalizeKeyword(String value) {
        String normalized = normalizeOptionalText(value);

        if (normalized != null
                && normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "T\u1EEB kh\u00F3a t\u00ECm ki\u1EBFm t\u1ED1i \u0111a 150 k\u00FD t\u1EF1."
            );
        }

        return normalized;
    }

    private String normalizeUrgency(String value) {
        String normalized = normalizeOptionalText(value);

        if (normalized == null) {
            return null;
        }

        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!URGENCIES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "M\u1EE9c \u0111\u1ED9 kh\u1EA9n c\u1EA5p kh\u00F4ng h\u1EE3p l\u1EC7."
            );
        }

        return normalized;
    }

    private void validateDateRange(CoordinatorRequestFilter filter) {
        if (filter.getFrom() != null
                && filter.getTo() != null
                && filter.getFrom().isAfter(filter.getTo())) {
            throw new IllegalArgumentException(
                    "Ng\u00E0y b\u1EAFt \u0111\u1EA7u kh\u00F4ng \u0111\u01B0\u1EE3c sau ng\u00E0y k\u1EBFt th\u00FAc."
            );
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
