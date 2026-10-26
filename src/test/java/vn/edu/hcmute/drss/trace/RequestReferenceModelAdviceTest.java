package vn.edu.hcmute.drss.trace;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestReferenceModelAdviceTest {

    private final RequestReferenceModelAdvice advice = new RequestReferenceModelAdvice();

    @Test
    void exposesOnlyServerAttributeToErrorView() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelation.HEADER, "attacker-value");
        assertThat(advice.reference(request)).isNull();

        String id = UUID.randomUUID().toString();
        request.setAttribute(RequestCorrelation.ATTRIBUTE, id);
        assertThat(advice.reference(request)).isEqualTo(id);
    }
}
