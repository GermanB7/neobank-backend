package com.neobank.auth.api.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Represents the authenticated user's profile information.
 * Returned by GET /auth/me endpoint.
 */
public record UserProfileResponse(
        UUID id,
        String email,
        Set<String> roles,
        boolean enabled
) {}

