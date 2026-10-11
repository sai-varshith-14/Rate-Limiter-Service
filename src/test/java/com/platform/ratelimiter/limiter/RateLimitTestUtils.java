package com.platform.ratelimiter.limiter;

import com.platform.ratelimiter.config.RateLimitProperties;

public class RateLimitTestUtils {

    /**
     * Builds RateLimitProperties with capacity 10, refill rate 1 token/sec,
     * reflecting a 60-second window / max requests configuration.
     */
    public static RateLimitProperties standardProperties() {
        return new RateLimitProperties(true, 10, 1);
    }
}
