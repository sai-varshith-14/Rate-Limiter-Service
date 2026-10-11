package com.platform.ratelimiter.limiter;

import com.platform.ratelimiter.config.RateLimitProperties;
import com.platform.ratelimiter.dto.RateLimitResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(
        name = "ratelimit.algorithm",
        havingValue = "token-bucket",
        matchIfMissing = true
)
public class TokenBucketRateLimiter implements RateLimiter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> store = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    private static class Bucket {
        double tokens;
        long lastRefillTimestamp;
        long lastAccess;

        Bucket(double capacity, long timestamp) {
            this.tokens = capacity;
            this.lastRefillTimestamp = timestamp;
            this.lastAccess = timestamp;
        }
    }

    @Override
    public RateLimitResult tryConsume(String key) {
        long now = clock.millis();
        double capacity = properties.capacity();
        double refillRate = properties.refillRate(); // Tokens added per second

        // Retrieve or initialize the token bucket for the specific key
        Bucket bucket = store.computeIfAbsent(key, k -> new Bucket(capacity, now));

        // Thread-safe atomic evaluation and consumption per bucket instance
        synchronized (bucket) {
            bucket.lastAccess = now;
            // 1. Refill tokens based on elapsed time
            long elapsedMillis = now - bucket.lastRefillTimestamp;
            if (elapsedMillis > 0) {
                double tokensToAdd = (elapsedMillis / 1000.0) * refillRate;
                bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);
                bucket.lastRefillTimestamp = now;
            }

            // 2. Attempt token consumption
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return new RateLimitResult(true, (long) bucket.tokens, 0);
            } else {
                // Calculate how many seconds to wait until at least 1 token is available
                double missingTokens = 1.0 - bucket.tokens;
                long retryAfterSeconds = (long) Math.ceil(missingTokens / refillRate);
                retryAfterSeconds = Math.max(1, retryAfterSeconds);

                return new RateLimitResult(false, (long) bucket.tokens, retryAfterSeconds);
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