package com.neobank.transfers.repository;

import com.neobank.transfers.domain.TransferEntity;
import com.neobank.transfers.domain.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<TransferEntity, UUID> {

    Optional<TransferEntity> findByInitiatedByUserIdAndIdempotencyKey(UUID initiatedByUserId, String idempotencyKey);

    List<TransferEntity> findByInitiatedByUserIdOrderByCreatedAtDesc(UUID initiatedByUserId);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from TransferEntity t
            where t.sourceAccountId = :sourceAccountId
              and t.status = :status
              and t.processedAt >= :from
              and t.processedAt < :to
            """)
    BigDecimal sumOutgoingAmountBySourceAndStatusBetween(
            UUID sourceAccountId,
            TransferStatus status,
            Instant from,
            Instant to
    );

    long countBySourceAccountIdAndStatusAndProcessedAtGreaterThanEqual(
            UUID sourceAccountId,
            TransferStatus status,
            Instant processedAt
    );
}
