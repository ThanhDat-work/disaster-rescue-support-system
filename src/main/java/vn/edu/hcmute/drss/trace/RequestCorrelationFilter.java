package vn.edu.hcmute.drss.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        // Always generate the ID here. A caller-controlled header cannot inject log text.
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        boolean failed = false;
        request.setAttribute(RequestCorrelation.ATTRIBUTE, requestId);
        response.setHeader(RequestCorrelation.HEADER, requestId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(
                RequestCorrelation.KEY, requestId)) {
            try {
                chain.doFilter(request, response);
            } catch (ServletException | IOException | RuntimeException exception) {
                failed = true;
                throw exception;
            } finally {
                // No URI, query, body, cookies, tokens or exception text in access logs.
                if (!request.getServletPath().startsWith("/css/")
                        && !request.getServletPath().startsWith("/js/")
                        && !request.getServletPath().startsWith("/images/")) {
                    LOGGER.info(
                            "http_completed requestId={} method={} status={} elapsedMs={}",
                            requestId,
                            safeMethod(request.getMethod()),
                            failed ? 500 : response.getStatus(),
                            TimeUnit.NANOSECONDS.toMillis(
                                    System.nanoTime() - start)
                    );
                }
            }
        }
    }

    private String safeMethod(String method) {
        if (method == null) return "OTHER";
        return switch (method) {
            case "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS" ->
                    method;
            default -> "OTHER";
        };
    }
}
