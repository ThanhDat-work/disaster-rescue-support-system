package vn.edu.hcmute.drss.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.drss.repositories.ProfileStatisticsRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileStatisticsServiceTest {
    @Mock private ProfileStatisticsRepository reports;
    @InjectMocks private ProfileStatisticsService service;

    @Test
    void citizenUsesOwnStatusAggregatesAndBoundedMonths() {
        UUID userId = UUID.randomUUID();
        when(reports.citizenStatuses(userId)).thenReturn(Map.of(
                "PENDING", 1L, "IN_PROGRESS", 2L, "CLOSED", 3L));
        when(reports.citizenMonths(org.mockito.ArgumentMatchers.eq(userId), any()))
                .thenReturn(Map.of());
        var result = service.forRole(userId, "ROLE_CITIZEN", LocalDateTime.now());
        assertThat(result.cards()).containsEntry("Tổng yêu cầu", 6L)
                .containsEntry("Đang xử lý", 2L)
                .containsEntry("Đã cứu hộ/đóng", 3L);
        assertThat(result.labels()).hasSize(6);
    }

    @Test
    void coordinatorActionsComeFromActorHistoryAggregates() {
        UUID userId = UUID.randomUUID();
        when(reports.coordinatorActions(userId)).thenReturn(Map.of(
                "VERIFIED", 2L, "ASSIGNED", 1L));
        when(reports.coordinatorWeeks(org.mockito.ArgumentMatchers.eq(userId), any()))
                .thenReturn(java.util.List.of());
        var result = service.forRole(userId, "ROLE_COORDINATOR", LocalDateTime.now());
        assertThat(result.cards()).containsEntry("Đã xác minh", 2L)
                .containsEntry("Đã phân công", 1L);
        assertThat(result.series()).containsKey("ASSIGNED");
        verify(reports).coordinatorActions(userId);
    }
}