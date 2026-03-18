package com.neobank.audit.service;

import com.neobank.audit.api.dto.AuditEventResponse;
import com.neobank.audit.domain.AuditEventEntity;
import com.neobank.audit.repository.AuditEventRepository;
import com.neobank.observability.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional
    public void recordEvent(
            String eventType,
            UUID actorUserId,
            String actorEmail,
            String resourceType,
            String resourceId,
            String outcome,
            String details
    ) {
        AuditEventEntity event = new AuditEventEntity();
        event.setEventType(eventType);
        event.setActorUserId(actorUserId);
        event.setActorEmail(normalize(actorEmail));
        event.setResourceType(normalize(resourceType));
        event.setResourceId(normalize(resourceId));
        event.setOutcome(outcome);
        event.setDetails(normalize(details));
        event.setTraceId(normalize(MDC.get(CorrelationIdFilter.MDC_KEY)));
        auditEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public AuditEventResponse getEvent(UUID auditEventId) {
        AuditEventEntity event = auditEventRepository.findById(auditEventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit event not found"));
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getEvents(String eventType, UUID actorUserId, String resourceType, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return auditEventRepository.search(
                        normalize(eventType),
                        actorUserId,
                        normalize(resourceType),
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void recordAdminSensitiveAccess(
            Authentication authentication,
            String resourceType,
            String resourceId,
            String outcome,
            String details
    ) {
        UUID actorUserId = null;
        String actorEmail = null;
        if (authentication != null) {
            actorEmail = authentication.getName();
        }

        recordEvent(
                "ADMIN_SENSITIVE_ACCESS",
                actorUserId,
                actorEmail,
                resourceType,
                resourceId,
                outcome,
                details
        );
    }

    private AuditEventResponse toResponse(AuditEventEntity event) {
        return new AuditEventResponse(
                event.getId(),
                event.getEventType(),
                event.getActorUserId(),
                event.getActorEmail(),
                event.getResourceType(),
                event.getResourceId(),
                event.getOutcome(),
                event.getDetails(),
                event.getTraceId(),
                event.getCreatedAt()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

