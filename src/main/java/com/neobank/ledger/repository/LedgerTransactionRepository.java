package com.neobank.ledger.repository;

import com.neobank.ledger.domain.LedgerTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransactionEntity, UUID> {

    Optional<LedgerTransactionEntity> findByRelatedTransferId(UUID relatedTransferId);

    boolean existsByRelatedTransferId(UUID relatedTransferId);
}
