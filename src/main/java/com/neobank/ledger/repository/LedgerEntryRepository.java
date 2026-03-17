package com.neobank.ledger.repository;

import com.neobank.ledger.domain.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    List<LedgerEntryEntity> findByLedgerTransactionIdOrderByCreatedAtAsc(UUID ledgerTransactionId);

    List<LedgerEntryEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}

