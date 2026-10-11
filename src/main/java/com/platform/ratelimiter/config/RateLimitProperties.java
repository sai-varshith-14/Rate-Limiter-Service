package com.platform.ratelimiter.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(
        boolean enabled,
        @Min(1) int capacity,
        @Min(1) int refillRate
) {}