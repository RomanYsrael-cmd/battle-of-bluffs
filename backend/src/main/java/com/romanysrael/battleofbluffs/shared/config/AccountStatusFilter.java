package com.romanysrael.battleofbluffs.shared.config;

import com.romanysrael.battleofbluffs.user.AccountPrincipal;
import com.romanysrael.battleofbluffs.user.AccountUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

final class AccountStatusFilter extends OncePerRequestFilter {
    private final AccountUserDetailsService accounts;

    AccountStatusFilter(AccountUserDetailsService accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountPrincipal principal
                && unavailable(principal)) {
            SecurityContextHolder.clearContext();
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"code\":\"ACCOUNT_DISABLED\",\"message\":\"This account is unavailable.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean unavailable(AccountPrincipal principal) {
        if (!principal.isEnabled()) {
            return true;
        }
        try {
            return !accounts.loadUserByUsername(principal.username()).isEnabled();
        } catch (UsernameNotFoundException exception) {
            return true;
        }
    }
}
