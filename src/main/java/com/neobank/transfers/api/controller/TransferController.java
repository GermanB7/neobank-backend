package com.neobank.transfers.api.controller;

import com.neobank.risk.service.RiskPolicyViolationException;
import com.neobank.transfers.api.dto.CreateTransferRequest;
import com.neobank.transfers.api.dto.TransferResponse;
import com.neobank.transfers.api.dto.TransferSummaryResponse;
import com.neobank.transfers.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            Authentication authentication
    ) {
        try {
            TransferResponse response = transferService.createTransfer(request, authentication);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (RiskPolicyViolationException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex);
        }
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponse> getTransfer(
            @PathVariable UUID transferId,
            Authentication authentication
    ) {
        TransferResponse response = transferService.getTransfer(transferId, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<TransferSummaryResponse>> getMyTransfers(Authentication authentication) {
        List<TransferSummaryResponse> response = transferService.getMyTransfers(authentication);
        return ResponseEntity.ok(response);
    }
}
