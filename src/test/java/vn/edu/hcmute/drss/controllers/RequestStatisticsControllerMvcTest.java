package vn.edu.hcmute.drss.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.hcmute.drss.services.RequestStatistics;
import vn.edu.hcmute.drss.services.RequestStatisticsService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class RequestStatisticsControllerMvcTest {

    @Mock
    private RequestStatisticsService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new RequestStatisticsController(service)).build();
    }

    @Test
    void missingDatesUsesServiceDefaultAndRendersDashboard()
            throws Exception {
        RequestStatistics statistics = statistics(
                LocalDate.of(2026, 9, 22),
                LocalDate.of(2026, 10, 21),
                2L);

        when(service.getStatistics(null, null))
                .thenReturn(statistics);

        mockMvc.perform(get("/stats/requests"))
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "pages/stats/request-statistics"))
                .andExpect(model().attributeExists("dashboard"))
                .andExpect(model().attribute(
                        "fromDate",
                        "2026-09-22"))
                .andExpect(model().attribute(
                        "toDate",
                        "2026-10-21"));

        verify(service).getStatistics(null, null);
    }

    @Test
    void validRangeIsParsedAndPassedToService()
            throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 10, 1);
        LocalDate toDate = LocalDate.of(2026, 10, 10);

        when(service.getStatistics(fromDate, toDate))
                .thenReturn(statistics(fromDate, toDate, 1L));

        mockMvc.perform(get("/stats/requests")
                .param("fromDate", "2026-10-01")
                .param("toDate", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("dashboard"))
                .andExpect(model().attributeDoesNotExist(
                        "errorMessage"));

        verify(service).getStatistics(fromDate, toDate);
    }

    @Test
    void serviceValidationErrorRendersErrorStateWithoutHttp500()
            throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 10, 20);
        LocalDate toDate = LocalDate.of(2026, 10, 10);

        when(service.getStatistics(fromDate, toDate))
                .thenThrow(new IllegalArgumentException(
                        "Kho\u1EA3ng ng\u00E0y kh\u00F4ng h\u1EE3p l\u1EC7"));

        mockMvc.perform(get("/stats/requests")
                .param("fromDate", "2026-10-20")
                .param("toDate", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(view().name(
                        "pages/stats/request-statistics"))
                .andExpect(model().attribute(
                        "errorMessage",
                        "Kho\u1EA3ng ng\u00E0y kh\u00F4ng h\u1EE3p l\u1EC7"))
                .andExpect(model().attributeDoesNotExist("dashboard"));
    }

    @Test
    void malformedDateRendersSafeErrorWithoutCallingService()
            throws Exception {
        mockMvc.perform(get("/stats/requests")
                .param("fromDate", "not-a-date")
                .param("toDate", "2026-10-10"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attributeDoesNotExist("dashboard"));

        verifyNoInteractions(service);
    }

    private RequestStatistics statistics(
            LocalDate fromDate,
            LocalDate toDate,
            long total) {
        Map<String, Long> statuses = new LinkedHashMap<>();
        statuses.put("PENDING", total);
        statuses.put("VERIFIED", 0L);
        statuses.put("ASSIGNED", 0L);
        statuses.put("IN_PROGRESS", 0L);
        statuses.put("RESCUED", 0L);
        statuses.put("CLOSED", 0L);
        statuses.put("REJECTED", 0L);
        statuses.put("CANCELLED", 0L);

        Map<String, Long> urgencies = new LinkedHashMap<>();
        urgencies.put("LOW", total);
        urgencies.put("MEDIUM", 0L);
        urgencies.put("HIGH", 0L);
        urgencies.put("CRITICAL", 0L);

        return new RequestStatistics(
                fromDate,
                toDate,
                total,
                statuses,
                urgencies);
    }
}