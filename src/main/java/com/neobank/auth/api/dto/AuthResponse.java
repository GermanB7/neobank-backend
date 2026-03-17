package com.neobank.auth.api.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {}