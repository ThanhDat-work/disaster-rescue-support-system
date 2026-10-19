package vn.edu.hcmute.drss.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;

import java.time.Duration;

@Service
public class JwtCookieService {

    private static final String SAME_SITE_POLICY = "Lax";
    private static final String COOKIE_PATH = "/";

    private final AuthSecurityProperties properties;

    public JwtCookieService(
            AuthSecurityProperties properties
    ) {
        this.properties = properties;
    }

    public ResponseCookie createAccessTokenCookie(
            IssuedAccessToken accessToken
    ) {
        if (accessToken == null) {
            throw new IllegalArgumentException(
                    "Issued access token is required"
            );
        }

        return baseCookie(accessToken.tokenValue())
                .maxAge(properties.accessTokenTtl())
                .build();
    }

    public ResponseCookie createDeletionCookie() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(
            String value
    ) {
        return ResponseCookie
                .from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(SAME_SITE_POLICY)
                .path(COOKIE_PATH);
    }
}