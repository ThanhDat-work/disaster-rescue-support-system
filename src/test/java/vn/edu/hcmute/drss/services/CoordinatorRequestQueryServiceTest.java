package vn.edu.hcmute.drss.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestDetail;
import vn.edu.hcmute.drss.dtos.CoordinatorRequestFilter;
import vn.edu.hcmute.drss.entities.DisasterType;
import vn.edu.hcmute.drss.entities.MediaAsset;
import vn.edu.hcmute.drss.entities.RescueRequest;
import vn.edu.hcmute.drss.entities.StatusHistory;
import vn.edu.hcmute.drss.exceptions.CoordinatorRequestNotFoundException;
import vn.edu.hcmute.drss.repositories.DisasterTypeRepository;
import vn.edu.hcmute.drss.repositories.MediaAssetRepository;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;
import vn.edu.hcmute.drss.repositories.StatusHistoryRepository;

@ExtendWith(MockitoExtension.class)
class CoordinatorRequestQueryServiceTest {

    private static final UUID COORDINATOR_ID = UUID.randomUUID();

    @Mock
    private RescueRequestRepository requests;
    @Mock
    private DisasterTypeRepository disasterTypes;
    @Mock
    private StatusHistoryRepository histories;
    @Mock
    private MediaAssetRepository mediaAssets;

    private CoordinatorRequestQueryService service;

    @BeforeEach
    void setUp() {
        service = new CoordinatorRequestQueryService(
                requests,
                disasterTypes,
                histories,
                mediaAssets
        );
    }

    @Test
    void pendingSearchNormalizesFiltersAndUsesInclusiveEndDate() {
        CoordinatorRequestFilter filter = new CoordinatorRequestFilter();
        filter.setKeyword("  Nguyen Van A  ");
        filter.setUrgency(" high ");
        filter.setDisasterTypeId(7L);
        filter.setFrom(LocalDate.of(2026, 10, 1));
        filter.setTo(LocalDate.of(2026, 10, 21));

        when(requests.searchPendingForCoordinator(
                any(), any(), any(), any(), any(), eq(COORDINATOR_ID), any()
        )).thenReturn(Page.empty());

        service.findPending(COORDINATOR_ID, filter, -5);

        ArgumentCaptor<Pageable> pageable =
                ArgumentCaptor.forClass(Pageable.class);
        verify(requests).searchPendingForCoordinator(
                eq("Nguyen Van A"),
                eq("HIGH"),
                eq(7L),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 22, 0, 0)),
                eq(COORDINATOR_ID),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize())
                .isEqualTo(CoordinatorRequestQueryService.PAGE_SIZE);
    }

    @Test
    void blankFiltersBecomeNull() {
        CoordinatorRequestFilter filter = new CoordinatorRequestFilter();
        filter.setKeyword("   ");
        filter.setUrgency("   ");

        when(requests.searchPendingForCoordinator(
                any(), any(), any(), any(), any(), eq(COORDINATOR_ID), any()
        )).thenReturn(Page.empty());

        service.findPending(COORDINATOR_ID, filter, 0);

        verify(requests).searchPendingForCoordinator(
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(COORDINATOR_ID),
                any(Pageable.class)
        );
    }

    @Test
    void overflowPageIsRequeriedAtLastAvailablePage() {
        Page<RescueRequest> overflow = new PageImpl<>(
                List.of(),
                PageRequest.of(99, CoordinatorRequestQueryService.PAGE_SIZE),
                21
        );
        RescueRequest request = new RescueRequest();
        Page<RescueRequest> last = new PageImpl<>(
                List.of(request),
                PageRequest.of(2, CoordinatorRequestQueryService.PAGE_SIZE),
                21
        );

        when(requests.searchPendingForCoordinator(
                any(), any(), any(), any(), any(), eq(COORDINATOR_ID), any()
        )).thenReturn(overflow, last);

        assertThat(service.findPending(COORDINATOR_ID, new CoordinatorRequestFilter(), 99))
                .isSameAs(last);

        ArgumentCaptor<Pageable> pageable =
                ArgumentCaptor.forClass(Pageable.class);
        verify(requests, times(2)).searchPendingForCoordinator(
                any(), any(), any(), any(), any(), eq(COORDINATOR_ID), pageable.capture()
        );
        assertThat(pageable.getAllValues().get(1).getPageNumber())
                .isEqualTo(2);
    }

    @Test
    void invalidUrgencyDateRangeAndLongKeywordAreRejected() {
        CoordinatorRequestFilter invalidUrgency =
                new CoordinatorRequestFilter();
        invalidUrgency.setUrgency("FORGED");
        assertThatThrownBy(() -> service.findPending(COORDINATOR_ID, invalidUrgency, 0))
                .isInstanceOf(IllegalArgumentException.class);

        CoordinatorRequestFilter invalidDates =
                new CoordinatorRequestFilter();
        invalidDates.setFrom(LocalDate.of(2026, 10, 22));
        invalidDates.setTo(LocalDate.of(2026, 10, 21));
        assertThatThrownBy(() -> service.findPending(COORDINATOR_ID, invalidDates, 0))
                .isInstanceOf(IllegalArgumentException.class);

        CoordinatorRequestFilter longKeyword =
                new CoordinatorRequestFilter();
        longKeyword.setKeyword("x".repeat(151));
        assertThatThrownBy(() -> service.findPending(COORDINATOR_ID, longKeyword, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detailAggregatesRequestHistoryAndNonDeletedMedia() {
        UUID requestId = UUID.randomUUID();
        RescueRequest request = new RescueRequest();
        StatusHistory history = new StatusHistory();
        MediaAsset media = new MediaAsset();

        when(requests.findCoordinatorDetailByIdAndOwnerExcluded(requestId, COORDINATOR_ID))
                .thenReturn(Optional.of(request));
        when(histories.findByRequest_IdOrderByCreatedAtAscIdAsc(requestId))
                .thenReturn(List.of(history));
        when(mediaAssets
                .findByRequest_IdAndDeletedAtIsNullOrderByCreatedAtAsc(requestId))
                .thenReturn(List.of(media));

        CoordinatorRequestDetail detail = service.getDetail(requestId, COORDINATOR_ID);

        assertThat(detail.request()).isSameAs(request);
        assertThat(detail.history()).containsExactly(history);
        assertThat(detail.media()).containsExactly(media);
    }

    @Test
    void missingDetailReturnsNotFound() {
        UUID requestId = UUID.randomUUID();
        when(requests.findCoordinatorDetailByIdAndOwnerExcluded(requestId, COORDINATOR_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(requestId, COORDINATOR_ID))
                .isInstanceOf(CoordinatorRequestNotFoundException.class);
    }

    @Test
    void ownerCannotLoadCoordinatorDetail() {
        UUID requestId = UUID.randomUUID();
        when(requests.findCoordinatorDetailByIdAndOwnerExcluded(
                requestId, COORDINATOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(requestId, COORDINATOR_ID))
                .isInstanceOf(CoordinatorRequestNotFoundException.class);
    }

    @Test
    void activeDisasterTypesAreReturnedInRepositoryOrder() {
        DisasterType flood = new DisasterType();
        DisasterType storm = new DisasterType();
        when(disasterTypes.findByIsActiveTrueOrderByDisasterNameAsc())
                .thenReturn(List.of(flood, storm));

        assertThat(service.getActiveDisasterTypes())
                .containsExactly(flood, storm);
    }
}