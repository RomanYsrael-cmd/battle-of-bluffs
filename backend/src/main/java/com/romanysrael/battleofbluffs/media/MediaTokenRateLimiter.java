package com.romanysrael.battleofbluffs.media;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class MediaTokenRateLimiter {
    private static final int MAXIMUM_KEYS = 10_000;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maximum;
    private final Duration window;

    MediaTokenRateLimiter(
            Clock clock,
            @Value("${app.rate-limit.media-token.limit:10}") int maximum,
            @Value("${app.rate-limit.media-token.window:1m}") Duration window) {
        if (maximum < 1 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Media token rate limits must be positive");
        }
        this.clock = clock;
        this.maximum = maximum;
        this.window = window;
    }

    synchronized void requirePermit(String accountId) {
        Instant now = clock.instant();
        if (!windows.containsKey(accountId) && windows.size() >= MAXIMUM_KEYS) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
            if (windows.size() >= MAXIMUM_KEYS) {
                throw limited();
            }
        }
        Window updated = windows.compute(accountId, (ignored, current) ->
                current == null || !now.isBefore(current.expiresAt())
                        ? new Window(now.plus(window), 1)
                        : new Window(current.expiresAt(), current.count() + 1));
        if (updated.count() > maximum) {
            throw limited();
        }
    }

    private MediaException limited() {
        return new MediaException("MEDIA_RATE_LIMITED", "Too many media connection attempts. Try again shortly.");
    }

    private record Window(Instant expiresAt, int count) { }
}
