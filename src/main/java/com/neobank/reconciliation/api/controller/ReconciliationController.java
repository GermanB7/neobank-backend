package com.neobank.reconciliation.api.controller;

import com.neobank.reconciliation.api.dto.ReconciliationDiscrepancyResponse;
import com.neobank.reconciliation.api.dto.ReconciliationReportResponse;
import com.neobank.reconciliation.service.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/run")
    public ResponseEntity<ReconciliationReportResponse> run(Authentication authentication) {
        ReconciliationReportResponse response = reconciliationService.runFullReconciliation(authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ReconciliationReportResponse>> listReports() {
        return ResponseEntity.ok(reconciliationService.listReports());
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<ReconciliationReportResponse> getReport(@PathVariable UUID reportId) {
        return ResponseEntity.ok(reconciliationService.getReport(reportId));
    }

    @GetMapping("/reports/{reportId}/discrepancies")
    public ResponseEntity<List<ReconciliationDiscrepancyResponse>> getDiscrepancies(@PathVariable UUID reportId) {
        return ResponseEntity.ok(reconciliationService.getDiscrepancies(reportId));
    }
}

