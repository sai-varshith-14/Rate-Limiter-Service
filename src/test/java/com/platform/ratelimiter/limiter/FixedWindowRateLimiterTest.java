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

class FixedWindowRateLimiterTest {

    private MutableClock clock;
    private RateLimitProperties properties;
    private FixedWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        // Capacity: 10 max requests per 1-minute window
        properties = new RateLimitProperties(true, 10, 1);
        rateLimiter = new FixedWindowRateLimiter(properties, clock);
    }

    @Test
    void allowsExactlyMaxRequestsThenRejects() {
        String key = "client-1";

        // Consume all 10 allowed requests in the current window
        for (int i = 0; i < 10; i++) {
            RateLimitResult result = rateLimiter.tryConsume(key);
            assertTrue(result.allowed(), "Request " + (i + 1) + " should be allowed");
        }

        // The 11th request must be rejected
        RateLimitResult blockedResult = rateLimiter.tryConsume(key);
        assertFalse(blockedResult.allowed(), "11th request should be blocked");
        assertTrue(blockedResult.retryAfterSeconds() >= 1, "Retry-After should be at least 1 second");
    }

    @Test
    void allowsAgainAfterAdvancingClockPastWindow() {
        String key = "client-1";

        // Exhaust the 10-request capacity
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume(key);
        }
        assertFalse(rateLimiter.tryConsume(key).allowed(), "Should be rate limited in current window");

        // Advance the clock past the 1-minute window boundary
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));

        // Quota should reset in the new window, allowing requests again
        assertTrue(rateLimiter.tryConsume(key).allowed(), "Request should be allowed after moving to the next window");
    }

    @Test
    void keysAreIndependent() {
        String clientA = "client-A";
        String clientB = "client-B";

        // Exhaust client A's window quota completely
        for (int i = 0; i < 10; i++) {
            rateLimiter.tryConsume(clientA);
        }
        assertFalse(rateLimiter.tryConsume(clientA).allowed(), "Client A should be rate limited");

        // Client B should still have its own independent quota available
        assertTrue(rateLimiter.tryConsume(clientB).allowed(), "Client B should have an independent quota");
    }
}
