package com.romanysrael.battleofbluffs.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class AbsoluteSessionLifetimeFilterTest {
    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedSessionAtAbsoluteLimitIsInvalidated() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getCreationTime()).thenReturn(NOW.minus(Duration.ofHours(12)).toEpochMilli());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(session);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        verify(session).invalidate();
        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("SESSION_EXPIRED");
    }

    @Test
    void youngerAuthenticatedSessionContinuesNormally() throws Exception {
        HttpSession session = mock(HttpSession.class);
        when(session.getCreationTime()).thenReturn(NOW.minus(Duration.ofHours(1)).toEpochMilli());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(session);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any());
        verify(session, never()).invalidate();
    }

    private static AbsoluteSessionLifetimeFilter filter() {
        return new AbsoluteSessionLifetimeFilter(
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(12));
    }
}
