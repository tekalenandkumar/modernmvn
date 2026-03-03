package com.modernmvn.backend.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks if the request should be allowed based on rate limits.
     * Uses a simple sliding window-ish approach (fixed window for simplicity).
     * 
     * @param key             The unique key for rate limiting (e.g. IP address +
     *                        ":" + endpoint)
     * @param limit           The maximum number of requests allowed in the window
     * @param durationMinutes The duration of the window in minutes
     * @return true if the request is allowed, false otherwise
     */
    public boolean tryConsume(String key, int limit, int durationMinutes) {
        String fullKey = "rate_limit:" + key;

        // Use Redis INCR and EXPIRE for a simple fixed-window rate limiter
        Long count = redisTemplate.opsForValue().increment(fullKey);

        if (count != null && count == 1) {
            // First request in the window, set expiration
            Duration expiry = Duration.ofMinutes(durationMinutes);
            redisTemplate.expire(fullKey, expiry);
        }

        return count != null && count <= limit;
    }
}
