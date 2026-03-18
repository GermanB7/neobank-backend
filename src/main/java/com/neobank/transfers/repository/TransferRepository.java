package com.neobank.transfers.repository;

import com.neobank.transfers.domain.TransferEntity;
import com.neobank.transfers.domain.TransferKind;
import com.neobank.transfers.domain.TransferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<TransferEntity, UUID> {

    Optional<TransferEntity> findByInitiatedByUserIdAndIdempotencyKey(UUID initiatedByUserId, String idempotencyKey);

    List<TransferEntity> findByInitiatedByUserIdOrderByCreatedAtDesc(UUID initiatedByUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransferEntity t where t.id = :id")
    Optional<TransferEntity> findByIdForUpdate(UUID id);

    boolean existsByOriginalTransferIdAndKind(UUID originalTransferId, TransferKind kind);

    Optional<TransferEntity> findByOriginalTransferIdAndKind(UUID originalTransferId, TransferKind kind);

    List<TransferEntity> findByStatus(TransferStatus status);

    List<TransferEntity> findByStatusAndKind(TransferStatus status, TransferKind kind);

    List<TransferEntity> findByKind(TransferKind kind);

    @Query("""
            select t.originalTransferId as originalTransferId,
                   count(t.id) as reversalCount
            from TransferEntity t
            where t.kind = :kind
              and t.originalTransferId is not null
            group by t.originalTransferId
            having count(t.id) > 1
            """)
    List<DuplicateReversalProjection> findDuplicateReversalOriginals(TransferKind kind);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from TransferEntity t
            where t.sourceAccountId = :sourceAccountId
              and (t.status = :completedStatus or t.status = :reversedStatus)
              and t.kind = :kind
              and t.processedAt >= :from
              and t.processedAt < :to
            """)
    BigDecimal sumOutgoingAmountBySourceAndTerminalStatusesAndKindBetween(
            UUID sourceAccountId,
            TransferStatus completedStatus,
            TransferStatus reversedStatus,
            TransferKind kind,
            Instant from,
            Instant to
    );

    @Query("""
            select count(t)
            from TransferEntity t
            where t.sourceAccountId = :sourceAccountId
              and (t.status = :completedStatus or t.status = :reversedStatus)
              and t.kind = :kind
              and t.processedAt >= :processedAt
            """)
    long countBySourceAccountIdAndTerminalStatusesAndKindAndProcessedAtGreaterThanEqual(
            UUID sourceAccountId,
            TransferStatus completedStatus,
            TransferStatus reversedStatus,
            TransferKind kind,
            Instant processedAt
    );

    interface DuplicateReversalProjection {
        UUID getOriginalTransferId();

        long getReversalCount();
    }
}
