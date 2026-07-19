package com.romanysrael.battleofbluffs.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class AbsoluteSessionLifetimeFilter extends OncePerRequestFilter {
    private final Clock clock;
    private final Duration absoluteTimeout;

    AbsoluteSessionLifetimeFilter(Clock clock, Duration absoluteTimeout) {
        if (absoluteTimeout.isNegative() || absoluteTimeout.isZero()) {
            throw new IllegalArgumentException("Absolute session timeout must be positive");
        }
        this.clock = clock;
        this.absoluteTimeout = absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (session != null
                && authentication != null
                && authentication.isAuthenticated()
                && clock.millis() - session.getCreationTime() >= absoluteTimeout.toMillis()) {
            SecurityContextHolder.clearContext();
            session.invalidate();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"SESSION_EXPIRED\",\"message\":\"Sign in to continue.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
