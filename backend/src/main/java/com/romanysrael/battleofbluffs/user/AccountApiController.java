package com.romanysrael.battleofbluffs.user;

import com.romanysrael.battleofbluffs.user.AccountService.AccountView;
import com.romanysrael.battleofbluffs.user.AccountService.RegisterAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
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
    Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of("headerName", csrfToken.getHeaderName(), "token", csrfToken.getToken());
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
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    public record RegistrationRequest(
            @NotBlank String username,
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String displayName) {
    }

    public record LoginRequest(@NotBlank String login, @NotBlank String password) {
    }

    public record TokenRequest(@NotBlank String token) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String password) {
    }

    public record GenericMessage(String message) {
    }
}
