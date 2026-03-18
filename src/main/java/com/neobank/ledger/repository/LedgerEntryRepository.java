package com.neobank.ledger.repository;

import com.neobank.ledger.domain.EntrySide;
import com.neobank.ledger.domain.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    List<LedgerEntryEntity> findByLedgerTransactionIdOrderByCreatedAtAsc(UUID ledgerTransactionId);

    List<LedgerEntryEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    boolean existsByLedgerTransactionId(UUID ledgerTransactionId);

    @Query("""
            select e.accountId as accountId,
                   e.currency as currency,
                   coalesce(sum(case when e.side = :debitSide then e.amount else -e.amount end), 0) as netAmount
            from LedgerEntryEntity e
            group by e.accountId, e.currency
            """)
    List<AccountLedgerNetProjection> aggregateNetBalancesByAccount(EntrySide debitSide);

    interface AccountLedgerNetProjection {
        UUID getAccountId();

        String getCurrency();

        BigDecimal getNetAmount();
    }
}
