package com.platform.ratelimiter.filter;

import com.platform.ratelimiter.config.RateLimitProperties;
import com.platform.ratelimiter.dto.RateLimitResult;
import com.platform.ratelimiter.limiter.RateLimiter;
import com.platform.ratelimiter.service.StatsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final StatsService statsService;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties, StatsService statsService) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.statsService = statsService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/stats".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-Key");
        String key = (apiKey != null && !apiKey.isBlank()) ? apiKey : request.getRemoteAddr();

        RateLimitResult result = rateLimiter.tryConsume(key);

        // Record statistics
        if (result.allowed()) {
            statsService.recordAllowed(key);
        } else {
            statsService.recordBlocked(key);
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(properties.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(String.format(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"retryAfterSeconds\":%d}",
                    result.retryAfterSeconds()
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }
}