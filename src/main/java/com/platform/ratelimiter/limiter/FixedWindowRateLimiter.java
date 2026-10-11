package com.platform.ratelimiter.limiter;

import com.platform.ratelimiter.config.RateLimitProperties;
import com.platform.ratelimiter.dto.RateLimitResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "ratelimit.algorithm", havingValue = "fixed-window")
public class FixedWindowRateLimiter implements RateLimiter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowState> store = new ConcurrentHashMap<>();

    // Fixed window duration (e.g., 1 minute)
    private final long windowSizeMillis = Duration.ofMinutes(1).toMillis();

    public FixedWindowRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    private static class WindowState {
        long windowStart;
        long count;
        long lastAccess;

        WindowState(long windowStart, long lastAccess) {
            this.windowStart = windowStart;
            this.count = 0;
            this.lastAccess = lastAccess;
        }
    }

    @Override
    public RateLimitResult tryConsume(String key) {
        long now = clock.millis();
        long currentWindowStart = now / windowSizeMillis;
        long limit = properties.capacity();

        // Ensure thread-safe retrieval or creation of the window state per key
        WindowState state = store.computeIfAbsent(key, k -> new WindowState(currentWindowStart, now));

        // Synchronize on the specific key's window state object for atomic counter updates
        synchronized (state) {
            state.lastAccess = now;
            // Check if we've transitioned to a new time window
            if (state.windowStart != currentWindowStart) {
                state.windowStart = currentWindowStart;
                state.count = 0;
            }

            if (state.count < limit) {
                state.count++;
                long remainingTokens = limit - state.count;
                return new RateLimitResult(true, remainingTokens, 0);
            } else {
                // Calculate seconds until the next window opens
                long nextWindowStart = (currentWindowStart + 1) * windowSizeMillis;
                long retryAfterSeconds = Math.max(1, (nextWindowStart - now) / 1000);
                return new RateLimitResult(false, 0, retryAfterSeconds);
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void evictIdleEntries() {
        long now = clock.millis();
        long idleThresholdMillis = 5 * 60 * 1000; // 5 minutes
        store.entrySet().removeIf(entry -> (now - entry.getValue().lastAccess) > idleThresholdMillis);
    }
}