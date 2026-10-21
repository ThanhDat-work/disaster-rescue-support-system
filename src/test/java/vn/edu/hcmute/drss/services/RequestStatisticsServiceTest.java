package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.repositories.RequestCountProjection;
import vn.edu.hcmute.drss.repositories.RescueRequestRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestStatisticsServiceTest {

    @Mock
    private RescueRequestRepository rescueRequestRepository;

    private RequestStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new RequestStatisticsService(
                rescueRequestRepository
        );
    }

    @Test
    void missingDatesUseThirtyInclusiveDaysEndingToday() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        LocalDate expectedFrom = today.minusDays(29);

        prepareEmptyAggregates(
                expectedFrom.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );

        RequestStatistics result = service.getStatistics(
                null,
                null,
                today
        );

        assertThat(result.fromDate()).isEqualTo(expectedFrom);
        assertThat(result.toDate()).isEqualTo(today);
        assertThat(result.totalRequests()).isZero();

        verify(rescueRequestRepository).countByStatusBetween(
                expectedFrom.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );

        verify(rescueRequestRepository).countByUrgencyBetween(
                expectedFrom.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );
    }

    @Test
    void aggregatesCountsAndZeroFillsMissingCategories() {
        LocalDate fromDate = LocalDate.of(2026, 10, 1);
        LocalDate toDate = LocalDate.of(2026, 10, 10);
        LocalDateTime fromInclusive = fromDate.atStartOfDay();
        LocalDateTime toExclusive =
                toDate.plusDays(1).atStartOfDay();

        when(rescueRequestRepository.countByStatusBetween(
                fromInclusive,
                toExclusive
        )).thenReturn(List.of(
                row("PENDING", 2L),
                row("CLOSED", 1L)
        ));

        when(rescueRequestRepository.countByUrgencyBetween(
                fromInclusive,
                toExclusive
        )).thenReturn(List.of(
                row("HIGH", 1L),
                row("CRITICAL", 2L)
        ));

        RequestStatistics result = service.getStatistics(
                fromDate,
                toDate,
                LocalDate.of(2026, 10, 21)
        );

        assertThat(result.totalRequests()).isEqualTo(3L);
assertThat(result.countsByStatus())
                .hasSize(8)
                .containsEntry("PENDING", 2L)
                .containsEntry("CLOSED", 1L)
                .containsEntry("VERIFIED", 0L)
                .containsEntry("CANCELLED", 0L);

        assertThat(result.countsByUrgency())
                .hasSize(4)
                .containsEntry("LOW", 0L)
                .containsEntry("MEDIUM", 0L)
                .containsEntry("HIGH", 1L)
                .containsEntry("CRITICAL", 2L);
    }

    @Test
    void onlyFromDateIsRejected() {
        assertThatThrownBy(() -> service.getStatistics(
                LocalDate.of(2026, 10, 1),
                null,
                LocalDate.of(2026, 10, 21)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đồng thời");
    }

    @Test
    void onlyToDateIsRejected() {
        assertThatThrownBy(() -> service.getStatistics(
                null,
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 21)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đồng thời");
    }

    @Test
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> service.getStatistics(
                LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 21)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được sau");
    }

    @Test
    void futureDateIsRejected() {
        assertThatThrownBy(() -> service.getStatistics(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 22),
                LocalDate.of(2026, 10, 21)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tương lai");
    }

    @Test
    void exactlyThreeHundredSixtySixDaysIsAccepted() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        LocalDate fromDate = today.minusDays(365);

        prepareEmptyAggregates(
                fromDate.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );

        RequestStatistics result = service.getStatistics(
                fromDate,
                today,
                today
        );

        assertThat(result.fromDate()).isEqualTo(fromDate);
        assertThat(result.toDate()).isEqualTo(today);
    }

    @Test
    void moreThanThreeHundredSixtySixDaysIsRejected() {
        LocalDate today = LocalDate.of(2026, 10, 21);

        assertThatThrownBy(() -> service.getStatistics(
                today.minusDays(366),
                today,
                today
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366 ngày");
    }

    private void prepareEmptyAggregates(
            LocalDateTime fromInclusive,
LocalDateTime toExclusive
    ) {
        when(rescueRequestRepository.countByStatusBetween(
                fromInclusive,
                toExclusive
        )).thenReturn(List.of());

        when(rescueRequestRepository.countByUrgencyBetween(
                fromInclusive,
                toExclusive
        )).thenReturn(List.of());
    }

    private RequestCountProjection row(
            String category,
            long total
    ) {
        return new RequestCountProjection() {
            @Override
            public String getCategory() {
                return category;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
