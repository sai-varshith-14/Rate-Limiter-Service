package com.platform.ratelimiter.limiter;

import com.platform.ratelimiter.config.RateLimitProperties;
import com.platform.ratelimiter.dto.RateLimitResult;
import com.platform.ratelimiter.limiter.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {

    private MutableClock clock;
    private RateLimitProperties properties;
    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        // Capacity: 10, Refill Rate: 1 token per second
        properties = new RateLimitProperties(true, 10, 1);
        rateLimiter = new TokenBucketRateLimiter(properties, clock);
    }

    @Test
    void allowsExactlyCapacityRequests() {
        String key = "client-1";

        for (int i = 0; i < 10; i++) {
            RateLimitResult result = rateLimiter.tryConsume(key);
            assertTrue(result.allowed(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void rejectsNextRequestWithRetryAfterSeconds() {
        String key = "client-1";

        // Exhaust capacity (10 requests)
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume(key);
        }

        // 11th request should be rejected
        RateLimitResult result = rateLimiter.tryConsume(key);
        assertFalse(result.allowed(), "11th request should be blocked");
        assertTrue(result.retryAfterSeconds() >= 1, "Retry-After should be at least 1 second");
    }

    @Test
    void refillsAfterAdvancingClockByTwoSeconds() {
        String key = "client-1";

        // Exhaust capacity
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume(key);
        }

        // Advance clock by 2 seconds (Adds 2 tokens at a 1 token/sec refill rate)
        clock.advance(Duration.ofSeconds(2));

        // Two more requests should pass successfully
        assertTrue(rateLimiter.tryConsume(key).allowed(), "First refilled request should be allowed");
        assertTrue(rateLimiter.tryConsume(key).allowed(), "Second refilled request should be allowed");

        // The subsequent request should be rejected again
        assertFalse(rateLimiter.tryConsume(key).allowed(), "Third request after 2-second refill should be rejected");
    }

    @Test
    void differentKeysHaveIndependentQuotas() {
        String clientA = "client-A";
        String clientB = "client-B";

        // Exhaust client A completely
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume(clientA);
        }
        assertFalse(rateLimiter.tryConsume(clientA).allowed(), clientA + " should be rate limited");

        // Client B should still have its full independent quota available
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.tryConsume(clientB).allowed(), "Client B request " + (i + 1) + " should be allowed");
        }
        assertFalse(rateLimiter.tryConsume(clientB).allowed(), clientB + " should be rate limited after 10 requests");
    }
}