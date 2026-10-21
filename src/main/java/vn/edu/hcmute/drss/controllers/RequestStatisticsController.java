package vn.edu.hcmute.drss.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.hcmute.drss.dtos.RequestStatisticsDashboard;
import vn.edu.hcmute.drss.services.RequestStatistics;
import vn.edu.hcmute.drss.services.RequestStatisticsService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/stats/requests")
public class RequestStatisticsController {

    private final RequestStatisticsService service;

    public RequestStatisticsController(
            RequestStatisticsService service) {
        this.service = service;
    }

    @GetMapping
    public String dashboard(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            Model model) {
        String normalizedFrom = normalize(fromDate);
        String normalizedTo = normalize(toDate);

        model.addAttribute("fromDate", normalizedFrom);
        model.addAttribute("toDate", normalizedTo);
        model.addAttribute("maxDate", LocalDate.now().toString());

        try {
            RequestStatistics statistics = service.getStatistics(
                    parseDate(normalizedFrom, "b\u1EAFt \u0111\u1EA7u"),
                    parseDate(normalizedTo, "k\u1EBFt th\u00FAc"));

            RequestStatisticsDashboard dashboard = RequestStatisticsDashboard.from(statistics);

            model.addAttribute("dashboard", dashboard);
            model.addAttribute(
                    "fromDate",
                    dashboard.fromDate().toString());
            model.addAttribute(
                    "toDate",
                    dashboard.toDate().toString());
        } catch (IllegalArgumentException exception) {
            model.addAttribute(
                    "errorMessage",
                    exception.getMessage());
        }

        return "pages/stats/request-statistics";
    }

    private LocalDate parseDate(
            String value,
            String fieldLabel) {
        if (value == null) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Ng\u00E0y " + fieldLabel
                            + " ph\u1EA3i \u0111\u00FAng \u0111\u1ECBnh d\u1EA1ng yyyy-MM-dd.");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}