package com.neobank.risk.api.controller;

import com.neobank.audit.service.AuditService;
import com.neobank.risk.api.dto.RiskEvaluationResponse;
import com.neobank.risk.domain.RiskEvaluationEntity;
import com.neobank.risk.repository.RiskEvaluationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/risk")
public class RiskController {

    private final RiskEvaluationRepository riskEvaluationRepository;
    private final AuditService auditService;

    public RiskController(RiskEvaluationRepository riskEvaluationRepository, AuditService auditService) {
        this.riskEvaluationRepository = riskEvaluationRepository;
        this.auditService = auditService;
    }

    @GetMapping("/evaluations/{riskEvaluationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RiskEvaluationResponse> getEvaluationById(
            @PathVariable UUID riskEvaluationId,
            Authentication authentication
    ) {
        RiskEvaluationEntity evaluation = riskEvaluationRepository.findById(riskEvaluationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk evaluation not found"));

        auditService.recordAdminSensitiveAccess(
                authentication,
                "RISK_EVALUATION",
                riskEvaluationId.toString(),
                "SUCCESS",
                "Admin read risk evaluation"
        );

        return ResponseEntity.ok(toResponse(evaluation));
    }

    @GetMapping("/transfers/{transferId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RiskEvaluationResponse> getEvaluationByTransferId(
            @PathVariable UUID transferId,
            Authentication authentication
    ) {
        RiskEvaluationEntity evaluation = riskEvaluationRepository.findTopByTransferIdOrderByCreatedAtDesc(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk evaluation not found for transfer"));

        auditService.recordAdminSensitiveAccess(
                authentication,
                "RISK_TRANSFER_LOOKUP",
                transferId.toString(),
                "SUCCESS",
                "Admin read risk evaluation by transfer"
        );

        return ResponseEntity.ok(toResponse(evaluation));
    }

    private RiskEvaluationResponse toResponse(RiskEvaluationEntity evaluation) {
        return new RiskEvaluationResponse(
                evaluation.getId(),
                evaluation.getTransferId(),
                evaluation.getSourceAccountId(),
                evaluation.getInitiatedByUserId(),
                evaluation.getAmount(),
                evaluation.getDecision(),
                evaluation.getRiskScore(),
                evaluation.getTriggeredRules(),
                evaluation.getReason(),
                evaluation.getCreatedAt()
        );
    }
}
