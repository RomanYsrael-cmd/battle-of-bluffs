package com.romanysrael.battleofbluffs.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AccountRateLimiterTest {
    @Test
    void eachConfiguredAccountBoundaryRejectsRequestsBeyondItsLimit() {
        AccountRateLimiter limiter = limiter(1);

        limiter.requireRegistration("client-a");
        limiter.requireLogin("client-a");
        limiter.requireForgotPassword("client-a");
        limiter.requireResetPassword("client-a");

        assertRateLimited(() -> limiter.requireRegistration("client-a"));
        assertRateLimited(() -> limiter.requireLogin("client-a"));
        assertRateLimited(() -> limiter.requireForgotPassword("client-a"));
        assertRateLimited(() -> limiter.requireResetPassword("client-a"));
    }

    @Test
    void attackerControlledKeysCannotGrowTheLimiterPastItsHardBound() {
        AccountRateLimiter limiter = limiter(2);
        for (int index = 0; index < AccountRateLimiter.MAXIMUM_KEYS; index++) {
            limiter.requireRegistration("client-" + index);
        }

        assertRateLimited(() -> limiter.requireRegistration("overflow-client"));
    }

    private static AccountRateLimiter limiter(int maximum) {
        Duration window = Duration.ofMinutes(15);
        return new AccountRateLimiter(
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC),
                maximum, window,
                maximum, window,
                maximum, window,
                maximum, window,
                maximum, window);
    }

    private static void assertRateLimited(Runnable request) {
        assertThatThrownBy(request::run)
                .isInstanceOf(AccountException.class)
                .extracting(exception -> ((AccountException) exception).code())
                .isEqualTo("RATE_LIMITED");
    }
}
