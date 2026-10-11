package com.platform.ratelimiter.limiter;

import com.platform.ratelimiter.dto.RateLimitResult;

public interface RateLimiter {
    RateLimitResult tryConsume(String key);
}
