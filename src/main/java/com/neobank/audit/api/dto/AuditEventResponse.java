package com.neobank.audit.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        String eventType,
        UUID actorUserId,
        String actorEmail,
        String resourceType,
        String resourceId,
        String outcome,
        String details,
        String traceId,
        Instant createdAt
) {
}

