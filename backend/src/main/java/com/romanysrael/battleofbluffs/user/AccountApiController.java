package com.romanysrael.battleofbluffs.user;

import com.romanysrael.battleofbluffs.user.AccountService.AccountView;
import com.romanysrael.battleofbluffs.user.AccountService.RegisterAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AccountApiController {
    private final AccountService accounts;
    private final AccountRateLimiter rateLimiter;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionRegistry sessionRegistry;

    public AccountApiController(
            AccountService accounts,
            AccountRateLimiter rateLimiter,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionRegistry sessionRegistry) {
        this.accounts = accounts;
        this.rateLimiter = rateLimiter;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionRegistry = sessionRegistry;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AccountView register(@Valid @RequestBody RegistrationRequest request, HttpServletRequest servletRequest) {
        rateLimiter.requireRegistration(clientKey(servletRequest));
        return accounts.register(new RegisterAccount(
                request.username(), request.email(), request.password(), request.displayName()));
    }

    @PostMapping("/login")
    AccountView login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        rateLimiter.requireLogin(clientKey(servletRequest));
        rateLimiter.requireLogin("account:" + normalizeLogin(request.login()));
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.login(), request.password()));
        } catch (AuthenticationException exception) {
            throw new AccountException("INVALID_CREDENTIALS", "Invalid username/email or password.");
        }
        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        sessionRegistry.registerNewSession(servletRequest.getSession().getId(), principal);
        return accounts.recordLogin(principal.userId());
    }

    @GetMapping("/me")
    AccountView me(Authentication authentication) {
        return accounts.get(principal(authentication).userId());
    }

    @GetMapping("/csrf")
    CsrfView csrf(CsrfToken csrfToken) {
        return new CsrfView(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @PostMapping("/verify-email")
    AccountView verifyEmail(@Valid @RequestBody TokenRequest request) {
        return accounts.verifyEmail(request.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resendVerification(Authentication authentication, HttpServletRequest servletRequest) {
        AccountPrincipal principal = principal(authentication);
        rateLimiter.requireResendVerification(principal.userId() + ":" + clientKey(servletRequest));
        accounts.resendVerification(principal.userId());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GenericMessage forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.requireForgotPassword(clientKey(servletRequest));
        accounts.requestPasswordReset(request.email());
        return new GenericMessage("If that account exists, a password reset message has been sent.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.requireResetPassword(clientKey(servletRequest));
        accounts.resetPassword(request.token(), request.password());
    }

    private static AccountPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AccountException("AUTHENTICATION_REQUIRED", "Sign in to continue.");
        }
        return principal;
    }

    private static String clientKey(HttpServletRequest request) {
        return "ip:" + (request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr());
    }

    private static String normalizeLogin(String login) {
        return java.text.Normalizer.normalize(login, java.text.Normalizer.Form.NFKC)
                .strip().toLowerCase(java.util.Locale.ROOT);
    }

    public record RegistrationRequest(
            @NotBlank @Size(max = 32) String username,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 72) String password,
            @NotBlank @Size(max = 50) String displayName) {
        @Override public String toString() { return "RegistrationRequest[REDACTED]"; }
    }

    public record LoginRequest(
            @NotBlank @Size(max = 320) String login,
            @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }

    public record TokenRequest(@NotBlank @Size(max = 128) String token) {
        @Override public String toString() { return "TokenRequest[REDACTED]"; }
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {
        @Override public String toString() { return "ForgotPasswordRequest[REDACTED]"; }
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "ResetPasswordRequest[REDACTED]"; }
    }

    public record GenericMessage(String message) {
    }

    public record CsrfView(String headerName, String token) {
        @Override public String toString() { return "CsrfView[headerName=" + headerName + ", token=REDACTED]"; }
    }
}
