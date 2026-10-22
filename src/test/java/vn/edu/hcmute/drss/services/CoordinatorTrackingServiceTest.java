package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

@ExtendWith(MockitoExtension.class)
class CoordinatorTrackingServiceTest {

    @Mock
    private RescueRequestRepository requests;
    @Mock
    private RescueAssignmentRepository assignments;
    @Mock
    private DisasterTypeRepository disasterTypes;
    @Mock
    private RescueTeamRepository teams;

    private CoordinatorTrackingService service;

    @BeforeEach
    void setUp() {
        service = new CoordinatorTrackingService(
                requests,
                assignments,
                disasterTypes,
                teams
        );
    }

    @Test
    void trackingNormalizesFiltersAndLoadsAssignmentsInOneBatch() {
        UUID teamId = UUID.randomUUID();
        RescueRequest request = request();
        RescueTeam team = new RescueTeam();
        team.setId(teamId);
        RescueAssignment assignment = new RescueAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setRequest(request);
        assignment.setTeam(team);

        CoordinatorTrackingFilter filter = new CoordinatorTrackingFilter();
        filter.setKeyword("  REQ-001  ");
        filter.setStatus(" rescued ");
        filter.setUrgency(" high ");
        filter.setDisasterTypeId(7L);
        filter.setTeamId(teamId);
        filter.setFrom(LocalDate.of(2026, 10, 1));
        filter.setTo(LocalDate.of(2026, 10, 21));

        Page<RescueRequest> page = new PageImpl<>(
                List.of(request),
                PageRequest.of(0, CoordinatorTrackingService.PAGE_SIZE),
                1
        );
        when(requests.searchForCoordinatorTracking(
                eq("REQ-001"),
                eq("RESCUED"),
                eq("HIGH"),
                eq(7L),
                eq(teamId),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 22, 0, 0)),
                any(Pageable.class)
        )).thenReturn(page);
        when(assignments
                .findByRequest_IdInOrderByAssignedAtDescIdDesc(
                        List.of(request.getId())
                )).thenReturn(List.of(assignment));

        Page<CoordinatorTrackingItem> result =
                service.findTracking(filter, -1);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.request()).isSameAs(request);
            assertThat(item.assignment()).isSameAs(assignment);
        });
        verify(assignments)
                .findByRequest_IdInOrderByAssignedAtDescIdDesc(
                        List.of(request.getId())
                );
    }

    @Test
    void emptyPageDoesNotQueryAssignments() {
        when(requests.searchForCoordinatorTracking(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<CoordinatorTrackingItem> result = service.findTracking(
                new CoordinatorTrackingFilter(),
                0
        );

        assertThat(result).isEmpty();
    }

    @Test
    void invalidStatusUrgencyDatesAndLongKeywordAreRejected() {
        CoordinatorTrackingFilter invalidStatus =
                new CoordinatorTrackingFilter();
        invalidStatus.setStatus("FORGED");
        assertThatThrownBy(() -> service.findTracking(invalidStatus, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trạng thái");

        CoordinatorTrackingFilter invalidUrgency =
                new CoordinatorTrackingFilter();
        invalidUrgency.setUrgency("FORGED");
        assertThatThrownBy(() -> service.findTracking(invalidUrgency, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("khẩn cấp");

        CoordinatorTrackingFilter invalidDates =
                new CoordinatorTrackingFilter();
        invalidDates.setFrom(LocalDate.of(2026, 10, 22));
        invalidDates.setTo(LocalDate.of(2026, 10, 21));
        assertThatThrownBy(() -> service.findTracking(invalidDates, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ngày bắt đầu");

        CoordinatorTrackingFilter longKeyword =
                new CoordinatorTrackingFilter();
        longKeyword.setKeyword("x".repeat(151));
        assertThatThrownBy(() -> service.findTracking(longKeyword, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("150");
    }

    @Test
    void latestAssignmentAndReferenceListsComeFromRepositories() {
        UUID requestId = UUID.randomUUID();
        RescueAssignment assignment = new RescueAssignment();
        DisasterType disasterType = new DisasterType();
        RescueTeam team = new RescueTeam();

        when(assignments
                .findFirstByRequest_IdOrderByAssignedAtDescIdDesc(requestId))
                .thenReturn(Optional.of(assignment));
        when(disasterTypes.findByIsActiveTrueOrderByDisasterNameAsc())
                .thenReturn(List.of(disasterType));
        when(teams.findAll(any(Sort.class))).thenReturn(List.of(team));

        assertThat(service.findLatestAssignment(requestId))
                .containsSame(assignment);
        assertThat(service.getActiveDisasterTypes())
                .containsExactly(disasterType);
        assertThat(service.getTeams()).containsExactly(team);
    }

    private RescueRequest request() {
        RescueRequest request = new RescueRequest();
        request.setId(UUID.randomUUID());
        request.setStatus("RESCUED");
        return request;
    }
}