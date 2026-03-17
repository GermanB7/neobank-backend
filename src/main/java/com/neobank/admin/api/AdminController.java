package com.neobank.admin.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints protected by ROLE_ADMIN.
 * These routes can only be accessed by users with ROLE_ADMIN.
 *
 * @PreAuthorize is declarative authorization at method level.
 * URL-level authorization is configured in SecurityConfig for /admin/** pattern.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    /**
     * Example admin-only endpoint to verify authorization.
     * Returns a simple confirmation that the admin is authenticated.
     *
     * @return health status for admin operations
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminHealthResponse> adminHealth() {
        return ResponseEntity.ok(new AdminHealthResponse("Admin panel is operational"));
    }

    /**
     * Simple admin health check response.
     */
    public record AdminHealthResponse(String status) {}
}

