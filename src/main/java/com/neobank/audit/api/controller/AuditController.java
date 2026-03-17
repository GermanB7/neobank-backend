package com.neobank.audit.api.controller;

import com.neobank.audit.api.dto.AuditEventResponse;
import com.neobank.audit.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/events/{auditEventId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuditEventResponse> getEvent(@PathVariable UUID auditEventId) {
        return ResponseEntity.ok(auditService.getEvent(auditEventId));
    }

    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditEventResponse>> getEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(auditService.getEvents(eventType, actorUserId, resourceType, limit));
    }
}

