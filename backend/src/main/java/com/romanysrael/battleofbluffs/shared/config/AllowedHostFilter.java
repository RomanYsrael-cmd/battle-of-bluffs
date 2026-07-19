package com.romanysrael.battleofbluffs.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

final class AllowedHostFilter extends OncePerRequestFilter {
    private final Set<String> allowedHosts;

    AllowedHostFilter(String configuredHosts) {
        this.allowedHosts = Stream.of(configuredHosts.split(","))
                .map(String::strip)
                .filter(host -> !host.isEmpty())
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("At least one allowed Host is required");
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String serverName = request.getServerName();
        if (serverName == null || !allowedHosts.contains(serverName.toLowerCase(Locale.ROOT))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"INVALID_HOST\",\"message\":\"The request host is not allowed.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
