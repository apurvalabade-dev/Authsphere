package com.authsphere.security;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    public RateLimitService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Fixed-window rate limiter using atomic Redis INCR.
     * The first request in a window sets the expiry; INCR itself is atomic,
     * so concurrent requests from the same IP can't race past the limit.
     */
    public boolean isAllowed(String key, int maxRequests, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count != null && count <= maxRequests;
    }

    public long getTtlSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return (ttl != null && ttl > 0) ? ttl : 1L;
    }
}
