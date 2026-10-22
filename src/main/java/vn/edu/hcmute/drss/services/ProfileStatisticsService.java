package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.drss.dtos.ProfileStatistics;
import vn.edu.hcmute.drss.repositories.ProfileStatisticsRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProfileStatisticsService {
    private final ProfileStatisticsRepository reports;

    public ProfileStatisticsService(ProfileStatisticsRepository reports) {
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public ProfileStatistics forRole(UUID userId, String role,
                                     LocalDateTime createdAt) {
        LocalDate today = LocalDate.now();
        return switch (role) {
            case "ROLE_CITIZEN" -> citizen(userId, today);
            case "ROLE_COORDINATOR" -> coordinator(userId, today);
            case "ROLE_RESCUER" -> rescuer(userId, today);
            case "ROLE_ADMIN" -> admin(userId, createdAt, today);
            default -> throw new IllegalArgumentException("Vai trò không hợp lệ.");
        };
    }

    private ProfileStatistics citizen(UUID userId, LocalDate today) {
        Map<String, Long> statuses = reports.citizenStatuses(userId);
        Map<String, Long> cards = new LinkedHashMap<>();
        cards.put("Tổng yêu cầu", sum(statuses));
        cards.put("Đang xử lý", get(statuses, "VERIFIED")
                + get(statuses, "ASSIGNED") + get(statuses, "IN_PROGRESS"));
        cards.put("Đã cứu hộ/đóng", get(statuses, "RESCUED")
                + get(statuses, "CLOSED"));
        YearMonth first = YearMonth.from(today).minusMonths(5);
        Map<String, Long> months = reports.citizenMonths(userId,
                first.atDay(1).atStartOfDay());
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String month = first.plusMonths(i).toString();
            labels.add(month);
            values.add(get(months, month));
        }
        return new ProfileStatistics(cards, statuses, labels,
                Map.of("Yêu cầu", values), List.of());
    }

    private ProfileStatistics coordinator(UUID userId, LocalDate today) {
        Map<String, Long> actions = reports.coordinatorActions(userId);
        Map<String, Long> cards = new LinkedHashMap<>();
        cards.put("Đã xác minh", get(actions, "VERIFIED"));
        cards.put("Đã từ chối", get(actions, "REJECTED"));
        cards.put("Đã phân công", get(actions, "ASSIGNED"));
        cards.put("Đã đóng", get(actions, "CLOSED"));
        List<Map<String, Object>> rows = reports.coordinatorWeeks(userId,
                today.minusWeeks(7).with(java.time.DayOfWeek.MONDAY).atStartOfDay());
        List<String> labels = weekLabels(today);
        Map<String, List<Long>> series = new LinkedHashMap<>();
        for (String category : List.of("VERIFIED", "REJECTED", "ASSIGNED", "CLOSED")) {
            List<Long> values = new ArrayList<>();
            for (String week : labels) {
                values.add(rows.stream()
                        .filter(row -> week.equals(row.get("period"))
                                && category.equals(row.get("category")))
                        .mapToLong(row -> ((Number) row.get("total")).longValue())
                        .sum());
            }
            series.put(category, values);
        }
        return new ProfileStatistics(cards, Map.of(), labels, series, List.of());
    }

    private ProfileStatistics rescuer(UUID userId, LocalDate today) {
        Map<String, Long> statuses = reports.teamTaskStatuses(userId);
        Map<String, Long> cards = new LinkedHashMap<>();
        cards.put("Nhiệm vụ của đội", sum(statuses));
        cards.put("Đang thực hiện", get(statuses, "ACCEPTED"));
        cards.put("Đã hoàn thành", get(statuses, "COMPLETED"));
        List<String> weeks = weekLabels(today);
        Map<String, Long> completed = reports.teamCompletedWeeks(userId,
                today.minusWeeks(7).with(java.time.DayOfWeek.MONDAY).atStartOfDay());
        List<Long> values = weeks.stream().map(week -> get(completed, week)).toList();
        return new ProfileStatistics(cards, statuses, weeks,
                Map.of("Đã hoàn thành", values), List.of());
    }

    private ProfileStatistics admin(UUID userId, LocalDateTime createdAt,
                                    LocalDate today) {
        Map<String, Long> cards = new LinkedHashMap<>();
        cards.put("Vai trò đang được cấp", reports.activeRoleCount(userId));
        cards.put("Số ngày có tài khoản", createdAt == null ? 0L
                : Math.max(0, ChronoUnit.DAYS.between(createdAt.toLocalDate(), today)));
        cards.put("Lần chuyển vai trò (30 ngày)", reports.successfulSwitches(userId,
                today.minusDays(30).atStartOfDay()));
        return new ProfileStatistics(cards, Map.of(), List.of(), Map.of(),
                reports.recentSwitchActivity(userId));
    }

    private List<String> weekLabels(LocalDate today) {
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY).minusWeeks(7);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            LocalDate date = monday.plusWeeks(i);
            WeekFields iso = WeekFields.ISO;
            result.add(date.get(iso.weekBasedYear()) + "-W"
                    + String.format("%02d", date.get(iso.weekOfWeekBasedYear())));
        }
        return result;
    }

    private long get(Map<String, Long> values, String key) {
        return values.getOrDefault(key, 0L);
    }

    private long sum(Map<String, Long> values) {
        return values.values().stream().mapToLong(Long::longValue).sum();
    }
}