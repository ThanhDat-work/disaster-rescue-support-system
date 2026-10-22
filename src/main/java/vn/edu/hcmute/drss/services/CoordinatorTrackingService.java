package vn.edu.hcmute.drss.services;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.CoordinatorTrackingFilter;
import vn.edu.hcmute.drss.dtos.CoordinatorTrackingItem;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.RescueAssignment;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.RescueTeam;
import vn.edu.hcmute.drss.repositories.DisasterTypeRepository;
import vn.edu.hcmute.drss.repositories.RescueAssignmentRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.RescueTeamRepository;

@Service
public class CoordinatorTrackingService {

    public static final int PAGE_SIZE = 10;
    public static final int MAX_KEYWORD_LENGTH = 150;

    private static final Set<String> STATUSES = Set.of(
            "PENDING",
            "VERIFIED",
            "ASSIGNED",
            "IN_PROGRESS",
            "RESCUED",
            "CLOSED",
            "REJECTED",
            "CANCELLED"
    );

    private static final Set<String> URGENCIES = Set.of(
            "LOW", "MEDIUM", "HIGH", "CRITICAL"
    );

    private final RescueRequestRepository requests;
    private final RescueAssignmentRepository assignments;
    private final DisasterTypeRepository disasterTypes;
    private final RescueTeamRepository teams;

    public CoordinatorTrackingService(
            RescueRequestRepository requests,
            RescueAssignmentRepository assignments,
            DisasterTypeRepository disasterTypes,
            RescueTeamRepository teams
    ) {
        this.requests = requests;
        this.assignments = assignments;
        this.disasterTypes = disasterTypes;
        this.teams = teams;
    }

    @Transactional(readOnly = true)
    public Page<CoordinatorTrackingItem> findTracking(
            UUID coordinatorId,
            CoordinatorTrackingFilter filter,
            int requestedPage
    ) {
        UUID excludedOwnerId = requireCoordinatorId(coordinatorId);
        CoordinatorTrackingFilter safeFilter = filter == null
                ? new CoordinatorTrackingFilter()
                : filter;

        String keyword = normalizeKeyword(safeFilter.getKeyword());
        String status = normalizeEnum(
                safeFilter.getStatus(),
                STATUSES,
                "Trạng thái yêu cầu không hợp lệ."
        );
        String urgency = normalizeEnum(
                safeFilter.getUrgency(),
                URGENCIES,
                "Mức độ khẩn cấp không hợp lệ."
        );
        validateDateRange(safeFilter);

        LocalDateTime fromInclusive = safeFilter.getFrom() == null
                ? null
                : safeFilter.getFrom().atStartOfDay();
        LocalDateTime toExclusive = safeFilter.getTo() == null
                ? null
                : safeFilter.getTo().plusDays(1).atStartOfDay();

        int pageNumber = Math.max(requestedPage, 0);
        Page<RescueRequest> requestPage = search(
                safeFilter,
                keyword,
                status,
                urgency,
                fromInclusive,
                toExclusive,
                excludedOwnerId,
                PageRequest.of(pageNumber, PAGE_SIZE)
        );

        if (requestPage.getTotalPages() > 0
                && pageNumber >= requestPage.getTotalPages()) {
            requestPage = search(
                    safeFilter,
                    keyword,
                    status,
                    urgency,
                    fromInclusive,
                    toExclusive,
                    excludedOwnerId,
                    PageRequest.of(
                            requestPage.getTotalPages() - 1,
                            PAGE_SIZE
                    )
            );
        }

        Map<UUID, RescueAssignment> latestAssignments = latestAssignments(
                requestPage.getContent()
        );
        List<CoordinatorTrackingItem> items = requestPage.getContent()
                .stream()
                .map(request -> new CoordinatorTrackingItem(
                        request,
                        latestAssignments.get(request.getId())
                ))
                .toList();

        return new PageImpl<>(
                items,
                requestPage.getPageable(),
                requestPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public Optional<RescueAssignment> findLatestAssignment(UUID requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return assignments
                .findFirstByRequest_IdOrderByAssignedAtDescIdDesc(requestId);
    }

    @Transactional(readOnly = true)
    public List<DisasterType> getActiveDisasterTypes() {
        return disasterTypes.findByIsActiveTrueOrderByDisasterNameAsc();
    }

    @Transactional(readOnly = true)
    public List<RescueTeam> getTeams() {
        return teams.findAll(Sort.by(
                Sort.Order.asc("teamName"),
                Sort.Order.asc("displayCode")
        ));
    }

    private Page<RescueRequest> search(
            CoordinatorTrackingFilter filter,
            String keyword,
            String status,
            String urgency,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            UUID excludedOwnerId,
            Pageable pageable
    ) {
        return requests.searchForCoordinatorTracking(
                keyword,
                status,
                urgency,
                filter.getDisasterTypeId(),
                filter.getTeamId(),
                fromInclusive,
                toExclusive,
                excludedOwnerId,
                pageable
        );
    }

    private Map<UUID, RescueAssignment> latestAssignments(
            List<RescueRequest> pageContent
    ) {
        List<UUID> requestIds = pageContent.stream()
                .map(RescueRequest::getId)
                .toList();
        if (requestIds.isEmpty()) {
            return Map.of();
        }

        return assignments
                .findByRequest_IdInOrderByAssignedAtDescIdDesc(requestIds)
                .stream()
                .collect(Collectors.toMap(
                        assignment -> assignment.getRequest().getId(),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    private String normalizeKeyword(String value) {
        String normalized = normalizeOptionalText(value);
        if (normalized != null
                && normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Từ khóa tìm kiếm tối đa 150 ký tự."
            );
        }
        return normalized;
    }

    private String normalizeEnum(
            String value,
            Set<String> allowed,
            String errorMessage
    ) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private void validateDateRange(CoordinatorTrackingFilter filter) {
        if (filter.getFrom() != null
                && filter.getTo() != null
                && filter.getFrom().isAfter(filter.getTo())) {
            throw new IllegalArgumentException(
                    "Ngày bắt đầu không được sau ngày kết thúc."
            );
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private UUID requireCoordinatorId(UUID coordinatorId) {
        if (coordinatorId == null) {
            throw new IllegalArgumentException(
                    "Điều phối viên xác thực không hợp lệ."
            );
        }
        return coordinatorId;
    }
}