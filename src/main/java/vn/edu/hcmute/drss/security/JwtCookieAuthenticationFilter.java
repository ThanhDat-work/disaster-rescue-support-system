package vn.edu.hcmute.drss.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.hcmute.drss.configs.AuthSecurityProperties;

import java.io.IOException;
import java.util.Optional;

public class JwtCookieAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtAuthenticationService authenticationService;
    private final String cookieName;
    private final WebAuthenticationDetailsSource detailsSource =
            new WebAuthenticationDetailsSource();

    public JwtCookieAuthenticationFilter(
            JwtAuthenticationService authenticationService,
            AuthSecurityProperties properties
    ) {
        this.authenticationService = authenticationService;
        this.cookieName = properties.cookieName();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication existingAuthentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (existingAuthentication == null) {
            extractToken(request)
                    .flatMap(authenticationService::authenticate)
                    .ifPresent(authentication ->
                            installAuthentication(
                                    request,
                                    authentication
                            )
                    );
        }

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(
            HttpServletRequest request
    ) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        String tokenValue = null;
        int matchingCookies = 0;

        for (Cookie cookie : cookies) {
            if (!cookieName.equals(cookie.getName())) {
                continue;
            }

            matchingCookies++;

            if (matchingCookies > 1) {
                /*
                 * Hai cookie cùng tên có thể gây cookie tossing hoặc
                 * khiến client/server hiểu khác nhau. Fail closed.
                 */
                return Optional.empty();
            }

            tokenValue = cookie.getValue();
        }

        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(tokenValue);
    }

    private void installAuthentication(
            HttpServletRequest request,
            Authentication authentication
    ) {
        if (authentication
                instanceof AbstractAuthenticationToken token) {
            token.setDetails(
                    detailsSource.buildDetails(request)
            );
        }

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }
}