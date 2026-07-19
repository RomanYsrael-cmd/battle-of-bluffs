package com.romanysrael.battleofbluffs.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public final class AccountRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public AccountRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void requirePermit(String category, String key, int limit, Duration duration) {
        Instant now = clock.instant();
        String bucket = category + ':' + key;
        Window updated = windows.compute(bucket, (ignored, current) -> {
            if (current == null || !now.isBefore(current.startedAt().plus(duration))) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), current.count() + 1);
        });
        if (updated.count() > limit) {
            throw new AccountException("RATE_LIMITED", "Too many requests. Try again later.");
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().startedAt().plus(duration)));
        }
    }

    private record Window(Instant startedAt, int count) {
    }
}
