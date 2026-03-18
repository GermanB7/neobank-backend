package com.neobank.ratelimit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for rate limiting using Redis.
 *
 * Uses Redis INCR with TTL to track request counts.
 * Supports both IP-based and user-based rate limiting strategies.
 *
 * Principles:
 * - IP-based: limits requests from a single IP address (prevents brute force)
 * - User-based: limits requests from an authenticated user (prevents API abuse)
 * - Keys expire automatically after window duration
 * - Lightweight: single INCR operation per request
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if a request is allowed based on rate limit.
     *
     * @param identifier IP address or user ID
     * @param endpoint   API endpoint identifier
     * @param maxRequests maximum requests allowed
     * @param windowSeconds time window in seconds
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String identifier, String endpoint, int maxRequests, int windowSeconds) {
        String key = buildKey(identifier, endpoint);

        try {
            Long count = redisTemplate.opsForValue().increment(key);

            // Set expiration only on first increment (count == 1)
            if (count != null && count == 1) {
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }

            boolean allowed = count != null && count <= maxRequests;

            if (!allowed) {
                log.warn("Rate limit exceeded for {} on {} (count={})", identifier, endpoint, count);
            }

            return allowed;
        } catch (Exception ex) {
            log.error("Rate limit check failed for {} on {}: {}", identifier, endpoint, ex.getMessage(), ex);
            // Fail open: allow request if Redis is unavailable
            return true;
        }
    }

    /**
     * Get current request count for monitoring/debugging.
     *
     * @param identifier IP address or user ID
     * @param endpoint   API endpoint identifier
     * @return current count, or 0 if key doesn't exist
     */
    public Long getCurrentCount(String identifier, String endpoint) {
        String key = buildKey(identifier, endpoint);
        Object value = redisTemplate.opsForValue().get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    /**
     * Reset rate limit counter (for testing or admin operations).
     *
     * @param identifier IP address or user ID
     * @param endpoint   API endpoint identifier
     */
    public void reset(String identifier, String endpoint) {
        String key = buildKey(identifier, endpoint);
        Boolean deleted = redisTemplate.delete(key);
        if (deleted != null && deleted) {
            log.info("Rate limit reset for {} on {}", identifier, endpoint);
        }
    }

    private String buildKey(String identifier, String endpoint) {
        return RATE_LIMIT_KEY_PREFIX + endpoint + ":" + identifier;
    }
}
