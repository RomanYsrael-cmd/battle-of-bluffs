package com.romanysrael.battleofbluffs.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class AccountRateLimiter {
    static final int MAXIMUM_KEYS = 10_000;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Limit registration;
    private final Limit login;
    private final Limit resendVerification;
    private final Limit forgotPassword;
    private final Limit resetPassword;

    public AccountRateLimiter(
            Clock clock,
            @Value("${app.rate-limit.registration.limit:5}") int registrationLimit,
            @Value("${app.rate-limit.registration.window:1h}") Duration registrationWindow,
            @Value("${app.rate-limit.login.limit:10}") int loginLimit,
            @Value("${app.rate-limit.login.window:15m}") Duration loginWindow,
            @Value("${app.rate-limit.resend-verification.limit:3}") int resendVerificationLimit,
            @Value("${app.rate-limit.resend-verification.window:1h}") Duration resendVerificationWindow,
            @Value("${app.rate-limit.forgot-password.limit:5}") int forgotPasswordLimit,
            @Value("${app.rate-limit.forgot-password.window:1h}") Duration forgotPasswordWindow,
            @Value("${app.rate-limit.reset-password.limit:10}") int resetPasswordLimit,
            @Value("${app.rate-limit.reset-password.window:15m}") Duration resetPasswordWindow) {
        this.clock = clock;
        this.registration = new Limit(registrationLimit, registrationWindow);
        this.login = new Limit(loginLimit, loginWindow);
        this.resendVerification = new Limit(resendVerificationLimit, resendVerificationWindow);
        this.forgotPassword = new Limit(forgotPasswordLimit, forgotPasswordWindow);
        this.resetPassword = new Limit(resetPasswordLimit, resetPasswordWindow);
    }

    public void requireRegistration(String clientKey) {
        requirePermit("register", clientKey, registration);
    }

    public void requireLogin(String clientKey) {
        requirePermit("login", clientKey, login);
    }

    public void requireResendVerification(String clientKey) {
        requirePermit("resend-verification", clientKey, resendVerification);
    }

    public void requireForgotPassword(String clientKey) {
        requirePermit("forgot-password", clientKey, forgotPassword);
    }

    public void requireResetPassword(String clientKey) {
        requirePermit("reset-password", clientKey, resetPassword);
    }

    private synchronized void requirePermit(String category, String key, Limit limit) {
        Instant now = clock.instant();
        String bucket = category + ':' + key;
        if (!windows.containsKey(bucket) && windows.size() >= MAXIMUM_KEYS) {
            removeExpired(now);
            if (windows.size() >= MAXIMUM_KEYS) {
                throw new AccountException("RATE_LIMITED", "Too many requests. Try again later.");
            }
        }
        Window updated = windows.compute(bucket, (ignored, current) -> {
            if (current == null || !now.isBefore(current.expiresAt())) {
                return new Window(now.plus(limit.window()), 1);
            }
            return new Window(current.expiresAt(), current.count() + 1);
        });
        if (updated.count() > limit.maximum()) {
            throw new AccountException("RATE_LIMITED", "Too many requests. Try again later.");
        }
    }

    private void removeExpired(Instant now) {
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record Limit(int maximum, Duration window) {
        private Limit {
            if (maximum < 1 || window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("Rate limits must use positive values");
            }
        }
    }

    private record Window(Instant expiresAt, int count) {
    }
}
