package com.neobank.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt,
        String deviceInfo,
        String ipAddress
) {}

