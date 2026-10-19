package vn.edu.hcmute.drss.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpAddressResolver {

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "HTTP request is required"
            );
        }

        String remoteAddress = request.getRemoteAddr();

        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }

        return remoteAddress.strip();
    }
}