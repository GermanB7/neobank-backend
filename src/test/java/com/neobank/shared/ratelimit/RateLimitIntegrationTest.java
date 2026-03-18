package com.neobank.shared.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for rate limiting functionality.
 *
 * Tests cover:
 * - Rate limit enforcement via RateLimitService
 * - Proper request counting with TTL-based keys
 * - Reset functionality for testing
 * - Multiple endpoints and identifiers tracked separately
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        // Clear any existing rate limit state for clean tests
        rateLimitService.reset("test-ip-1", "auth:login");
        rateLimitService.reset("test-ip-1", "auth:register");
        rateLimitService.reset("test-user-1", "transfers:create");
    }

    @Test
    void rateLimitServiceTracksRequestsPerIdentifierAndEndpoint() {
        String identifier = "test-user-1";
        String endpoint = "transfers:create";

        // All requests within limit should be allowed
        for (int i = 0; i < 10; i++) {
            boolean allowed = rateLimitService.isAllowed(identifier, endpoint, 10, 300);
            assertTrue(allowed, "Request " + (i + 1) + " should be allowed");
        }

        // 11th request should be denied
        boolean denied = rateLimitService.isAllowed(identifier, endpoint, 10, 300);
        assertFalse(denied, "Request 11 should be denied");
    }

    @Test
    void rateLimitServiceTracksDifferentEndpointsSeparately() {
        String identifier = "test-ip-1";
        String endpoint1 = "auth:login";
        String endpoint2 = "auth:register";

        // Fill endpoint1 quota
        for (int i = 0; i < 5; i++) {
            rateLimitService.isAllowed(identifier, endpoint1, 5, 300);
        }

        // endpoint1 should be limited
        assertFalse(rateLimitService.isAllowed(identifier, endpoint1, 5, 300));

        // endpoint2 should still have quota
        assertTrue(rateLimitService.isAllowed(identifier, endpoint2, 10, 300));
    }

    @Test
    void rateLimitServiceTracksDifferentIdentifiersSeparately() {
        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";
        String endpoint = "auth:login";

        // Fill quota for ip1
        for (int i = 0; i < 5; i++) {
            rateLimitService.isAllowed(ip1, endpoint, 5, 300);
        }

        // ip1 should be limited
        assertFalse(rateLimitService.isAllowed(ip1, endpoint, 5, 300));

        // ip2 should have independent quota
        assertTrue(rateLimitService.isAllowed(ip2, endpoint, 5, 300));
    }

    @Test
    void getCurrentCountReturnsAccurateRequestCount() {
        String identifier = "test-counter";
        String endpoint = "test:endpoint";

        assertEquals(0, rateLimitService.getCurrentCount(identifier, endpoint));

        rateLimitService.isAllowed(identifier, endpoint, 100, 300);
        assertEquals(1, rateLimitService.getCurrentCount(identifier, endpoint));

        rateLimitService.isAllowed(identifier, endpoint, 100, 300);
        assertEquals(2, rateLimitService.getCurrentCount(identifier, endpoint));

        rateLimitService.isAllowed(identifier, endpoint, 100, 300);
        assertEquals(3, rateLimitService.getCurrentCount(identifier, endpoint));
    }

    @Test
    void resetClearsRateLimitCounter() {
        String identifier = "test-reset";
        String endpoint = "test:endpoint";

        // Build up some requests
        for (int i = 0; i < 7; i++) {
            rateLimitService.isAllowed(identifier, endpoint, 100, 300);
        }

        assertEquals(7, rateLimitService.getCurrentCount(identifier, endpoint));

        // Reset
        rateLimitService.reset(identifier, endpoint);

        // Counter should be back to 0
        assertEquals(0, rateLimitService.getCurrentCount(identifier, endpoint));

        // Next request should succeed as normal
        assertTrue(rateLimitService.isAllowed(identifier, endpoint, 10, 300));
    }

    @Test
    void lowLimitEnforcesStrictly() {
        String identifier = "test-strict";
        String endpoint = "test:strict";

        // Allow only 1 request per window
        assertTrue(rateLimitService.isAllowed(identifier, endpoint, 1, 300));
        assertFalse(rateLimitService.isAllowed(identifier, endpoint, 1, 300));
        assertFalse(rateLimitService.isAllowed(identifier, endpoint, 1, 300));
    }

    @Test
    void zeroLimitRejectsAllRequests() {
        String identifier = "test-zero";
        String endpoint = "test:zero";

        assertFalse(rateLimitService.isAllowed(identifier, endpoint, 0, 300));
        assertFalse(rateLimitService.isAllowed(identifier, endpoint, 0, 300));
    }
}


