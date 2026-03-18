package com.neobank.shared.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark an endpoint or method for rate limiting.
 *
 * Rate limiting is applied based on the configured strategy:
 * - IP-based for public endpoints (login, register)
 * - User-based for authenticated endpoints (transfer)
 *
 * When a request exceeds the limit, a 429 Too Many Requests response is returned.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * The maximum number of requests allowed within the time window.
     */
    int maxRequests() default 10;

    /**
     * The time window in seconds.
     */
    int windowSeconds() default 300; // 5 minutes

    /**
     * Rate limit strategy: "IP" for IP-based, "USER" for authenticated user.
     */
    String strategy() default "IP";

    /**
     * Human-readable message for rate limit exceeded response.
     */
    String message() default "Too many requests. Please try again later.";
}

