package com.platform.ratelimiter.limiter;

import com.platform.ratelimiter.config.RateLimitProperties;
import com.platform.ratelimiter.dto.RateLimitResult;
import com.platform.ratelimiter.limiter.MutableClock;
import org.junit.jupiter.api.RepeatedTest;

import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenBucketRateLimiterConcurrencyTest {

    @RepeatedTest(10)
    void concurrentRequestsConformToCapacity() throws InterruptedException {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        RateLimitProperties properties = new RateLimitProperties(true, 10, 1);
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(properties, clock);

        int threadCount = 100;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await(); // Wait until all threads are ready and synchronized

                        RateLimitResult result = rateLimiter.tryConsume("same-key");
                        if (result.allowed()) {
                            allowedCount.incrementAndGet();
                        } else {
                            blockedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(); // Ensure all threads are spawned and waiting
            startLatch.countDown(); // Release all 100 threads simultaneously
            doneLatch.await(); // Wait for all threads to finish execution
        } finally {
            executor.shutdown();
        }

        assertEquals(10, allowedCount.get(), "Exactly 10 requests should be allowed under concurrency");
        assertEquals(90, blockedCount.get(), "Exactly 90 requests should be blocked under concurrency");
    }
}
