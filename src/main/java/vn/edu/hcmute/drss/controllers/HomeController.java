package vn.edu.hcmute.drss.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import vn.edu.hcmute.drss.repositories.RescueTeamRepository;
import vn.edu.hcmute.drss.services.RequestStatistics;
import vn.edu.hcmute.drss.services.RequestStatisticsService;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@Controller
public class HomeController {

    private final RequestStatisticsService requestStatistics;
    private final RescueTeamRepository rescueTeams;

    public HomeController(
            RequestStatisticsService requestStatistics,
            RescueTeamRepository rescueTeams
    ) {
        this.requestStatistics = requestStatistics;
        this.rescueTeams = rescueTeams;
    }

    @GetMapping("/")
    public String home(Model model) {
        try {
            RequestStatistics statistics = requestStatistics.getStatistics(null, null);
            Map<String, Long> counts = statistics.countsByStatus();
            model.addAttribute("homeMetrics", Map.of(
                    "total", statistics.totalRequests(),
                    "pending", counts.getOrDefault("PENDING", 0L),
                    "processing", counts.getOrDefault("VERIFIED", 0L)
                            + counts.getOrDefault("ASSIGNED", 0L)
                            + counts.getOrDefault("IN_PROGRESS", 0L),
                    "resolved", counts.getOrDefault("RESCUED", 0L)
                            + counts.getOrDefault("CLOSED", 0L),
                    "availableTeams", rescueTeams.countByStatus("AVAILABLE")
            ));
            model.addAttribute("homeMetricsAvailable", true);
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            model.addAttribute("homeMetricsRange", statistics.fromDate().format(dateFormat)
                    + " – " + statistics.toDate().format(dateFormat));
        } catch (DataAccessException exception) {
            model.addAttribute("homeMetricsAvailable", false);
        }
        return "pages/home";
    }
}
