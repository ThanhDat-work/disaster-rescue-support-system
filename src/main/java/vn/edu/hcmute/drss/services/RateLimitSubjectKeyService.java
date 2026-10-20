package vn.edu.hcmute.drss.services;

import org.springframework.stereotype.Service;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;
import vn.edu.hcmute.drss.entities.AuthRateLimitAction;
import vn.edu.hcmute.drss.entities.AuthRateLimitScope;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Pattern;

@Service
public class RateLimitSubjectKeyService {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final String DOMAIN = "drss-rate-limit:v1";

    private static final Pattern IPV4_OCTET_PATTERN =
            Pattern.compile("[0-9]{1,3}");

    private final SecretKey rateLimitSecretKey;

    public RateLimitSubjectKeyService(
            AuthSecurityProperties properties
    ) {
        this.rateLimitSecretKey = new SecretKeySpec(
                properties.rateLimitHmacSecret()
                        .getBytes(StandardCharsets.UTF_8),
                HMAC_SHA_256
        );
    }

    public String createEmailIpKey(
            AuthRateLimitAction action,
            String email,
            String ipAddress
    ) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedIp = normalizeIp(ipAddress);

        String subject = encodeSegment(normalizedEmail)
                + "|"
                + encodeSegment(normalizedIp);

        return calculateDigest(
                action,
                AuthRateLimitScope.EMAIL_IP,
                subject
        );
    }

    public String createIpKey(
            AuthRateLimitAction action,
            String ipAddress
    ) {
        String normalizedIp = normalizeIp(ipAddress);

        return calculateDigest(
                action,
                AuthRateLimitScope.IP,
                encodeSegment(normalizedIp)
        );
    }

    private String calculateDigest(
            AuthRateLimitAction action,
            AuthRateLimitScope scope,
            String subject
    ) {
        if (action == null) {
            throw new IllegalArgumentException(
                    "Rate-limit action is required"
            );
        }

        String payload = DOMAIN
                + "|"
                + action.name()
                + "|"
                + scope.name()
                + "|"
                + subject;

        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(rateLimitSecretKey);

            byte[] digest = mac.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to calculate rate-limit subject key",
                    exception
            );
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email is required for EMAIL_IP rate limiting"
            );
        }

        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "IP address is required"
            );
        }

        String value = ipAddress.strip();

        if (value.contains(":")) {
            return normalizeIpv6(value);
        }

        return normalizeIpv4(value);
    }

    private String normalizeIpv4(String value) {
        String[] octets = value.split("\\.", -1);

        if (octets.length != 4) {
            throw invalidIpAddress();
        }

        StringJoiner normalized = new StringJoiner(".");

        for (String octet : octets) {
            if (!IPV4_OCTET_PATTERN.matcher(octet).matches()) {
                throw invalidIpAddress();
            }

            int numericValue = Integer.parseInt(octet);

            if (numericValue > 255) {
                throw invalidIpAddress();
            }

            normalized.add(Integer.toString(numericValue));
        }

        return normalized.toString();
    }

    private String normalizeIpv6(String value) {
        if (value.contains("%")) {
            throw invalidIpAddress();
        }

        try {
            InetAddress address = InetAddress.getByName(value);

            return address.getHostAddress()
                    .toLowerCase(Locale.ROOT);
        } catch (UnknownHostException exception) {
            throw invalidIpAddress();
        }
    }

    private String encodeSegment(String value) {
        int byteLength = value
                .getBytes(StandardCharsets.UTF_8)
                .length;

        return byteLength + ":" + value;
    }

    private IllegalArgumentException invalidIpAddress() {
        return new IllegalArgumentException(
                "IP address must be numeric IPv4 or IPv6"
        );
    }
}