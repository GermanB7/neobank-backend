package com.neobank.risk.service;

public class RiskPolicyViolationException extends RuntimeException {

    public RiskPolicyViolationException(String reasonCode) {
        super("TRANSFER_REJECTED_BY_RISK_POLICY: " + reasonCode);
    }
}

