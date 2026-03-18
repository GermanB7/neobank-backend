package com.neobank.auth.api.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
    public AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
        this(accessToken, null, tokenType, expiresInSeconds);
    }
}
