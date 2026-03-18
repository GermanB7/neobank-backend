package com.neobank.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * AOP aspect for rate limiting enforcement.
 *
 * Intercepts methods annotated with @RateLimit and checks request count against Redis.
 * Supports two strategies:
 * - IP-based: uses client IP address from request
 * - USER-based: uses authenticated user identifier from SecurityContext
 *
 * Returns 429 Too Many Requests when limit is exceeded.
 *
 * This aspect is disabled in test profiles by default to avoid interfering with test setup.
 * Enable with: neobank.ratelimit.enabled=true
 */
@Aspect
@Component
@ConditionalOnProperty(name = "neobank.ratelimit.enabled", havingValue = "true", matchIfMissing = false)
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    public RateLimitAspect(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Around("@annotation(rateLimit)")
    public Object enforce(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String identifier = resolveIdentifier(rateLimit.strategy());
        String endpoint = extractEndpointName(joinPoint);

        boolean allowed = rateLimitService.isAllowed(
                identifier,
                endpoint,
                rateLimit.maxRequests(),
                rateLimit.windowSeconds()
        );

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    rateLimit.message()
            );
        }

        return joinPoint.proceed();
    }

    private String resolveIdentifier(String strategy) {
        if ("USER".equals(strategy)) {
            return resolveUserIdentifier();
        } else {
            return resolveIpAddress();
        }
    }

    private String resolveIpAddress() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }

        HttpServletRequest request = attrs.getRequest();
        String ipAddress = request.getHeader("X-Forwarded-For");

        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        } else {
            // X-Forwarded-For can contain multiple IPs; use the first one
            ipAddress = ipAddress.split(",")[0].trim();
        }

        return ipAddress;
    }

    private String resolveUserIdentifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "anonymous";
    }

    private String extractEndpointName(ProceedingJoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().getSimpleName() + ":" + joinPoint.getSignature().getName();
    }
}


