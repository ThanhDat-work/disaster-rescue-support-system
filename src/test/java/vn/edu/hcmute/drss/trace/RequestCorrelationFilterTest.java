package vn.edu.hcmute.drss.trace;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void rejectsCallerControlledIdAndCleansThreadContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile");
        request.addHeader(RequestCorrelation.HEADER, "injected\nsecret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (received, written) -> {
            String id = (String) received.getAttribute(RequestCorrelation.ATTRIBUTE);
            assertThat(id).isEqualTo(MDC.get(RequestCorrelation.KEY));
            assertThat(id).isNotEqualTo("injected\nsecret");
            assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        });

        assertThat(response.getHeader(RequestCorrelation.HEADER))
                .isEqualTo(request.getAttribute(RequestCorrelation.ATTRIBUTE));
        assertThat(MDC.get(RequestCorrelation.KEY)).isNull();
    }

    @Test
    void newRequestGetsDifferentId() throws Exception {
        MockHttpServletRequest one = new MockHttpServletRequest("GET", "/profile");
        MockHttpServletRequest two = new MockHttpServletRequest("GET", "/profile");
        MockHttpServletResponse responseOne = new MockHttpServletResponse();
        MockHttpServletResponse responseTwo = new MockHttpServletResponse();

        filter.doFilter(one, responseOne, (request, response) -> {});
        filter.doFilter(two, responseTwo, (request, response) -> {});

        assertThat(responseOne.getHeader(RequestCorrelation.HEADER))
                .isNotEqualTo(responseTwo.getHeader(RequestCorrelation.HEADER));
    }
}
