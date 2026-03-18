package com.neobank.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ObservabilityMetrics {

    private final Counter authLoginSuccess;
    private final Counter authLoginFailure;
    private final Counter accountsCreated;
    private final Counter transfersCompleted;
    private final Counter transfersFailed;
    private final Counter transfersRejected;
    private final Counter riskAllow;
    private final Counter riskReject;
    private final Counter ledgerRecorded;
    private final Counter reconciliationRuns;
    private final Counter reconciliationDiscrepancies;

    private final MeterRegistry meterRegistry;

    public ObservabilityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.authLoginSuccess = Counter.builder("neobank.auth.login.success").register(meterRegistry);
        this.authLoginFailure = Counter.builder("neobank.auth.login.failure").register(meterRegistry);
        this.accountsCreated = Counter.builder("neobank.accounts.created").register(meterRegistry);
        this.transfersCompleted = Counter.builder("neobank.transfers.completed").register(meterRegistry);
        this.transfersFailed = Counter.builder("neobank.transfers.failed").register(meterRegistry);
        this.transfersRejected = Counter.builder("neobank.transfers.rejected").register(meterRegistry);
        this.riskAllow = Counter.builder("neobank.risk.allow").register(meterRegistry);
        this.riskReject = Counter.builder("neobank.risk.reject").register(meterRegistry);
        this.ledgerRecorded = Counter.builder("neobank.ledger.recorded").register(meterRegistry);
        this.reconciliationRuns = Counter.builder("neobank.reconciliation.runs").register(meterRegistry);
        this.reconciliationDiscrepancies = Counter.builder("neobank.reconciliation.discrepancies").register(meterRegistry);
    }

    public void incrementLoginSuccess() {
        authLoginSuccess.increment();
    }

    public void incrementLoginFailure() {
        authLoginFailure.increment();
    }

    public void incrementAccountsCreated() {
        accountsCreated.increment();
    }

    public void incrementTransfersCompleted() {
        transfersCompleted.increment();
    }

    public void incrementTransfersFailed() {
        transfersFailed.increment();
    }

    public void incrementTransfersRejected() {
        transfersRejected.increment();
    }

    public void incrementRiskAllow() {
        riskAllow.increment();
    }

    public void incrementRiskReject() {
        riskReject.increment();
    }

    public void incrementLedgerRecorded() {
        ledgerRecorded.increment();
    }

    public void incrementReconciliationRuns() {
        reconciliationRuns.increment();
    }

    public void incrementReconciliationDiscrepancies(int count) {
        if (count > 0) {
            reconciliationDiscrepancies.increment(count);
        }
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordTransferExecution(Timer.Sample sample) {
        sample.stop(Timer.builder("neobank.transfers.execution").register(meterRegistry));
    }

    public void recordRiskEvaluation(Timer.Sample sample) {
        sample.stop(Timer.builder("neobank.risk.evaluation").register(meterRegistry));
    }

    public void recordLedgerRecording(Timer.Sample sample) {
        sample.stop(Timer.builder("neobank.ledger.recording").register(meterRegistry));
    }
}
