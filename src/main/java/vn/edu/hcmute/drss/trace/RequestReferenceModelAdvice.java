package vn.edu.hcmute.drss.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class RequestReferenceModelAdvice {

    @ModelAttribute("requestCorrelationId")
    public String reference(HttpServletRequest request) {
        Object value = request.getAttribute(RequestCorrelation.ATTRIBUTE);
        return value instanceof String id ? id : null;
    }
}
