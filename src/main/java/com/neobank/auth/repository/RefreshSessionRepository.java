package com.neobank.auth.repository;

import com.neobank.auth.domain.RefreshSessionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSessionEntity, UUID> {

    Optional<RefreshSessionEntity> findByTokenHash(String tokenHash);

    List<RefreshSessionEntity> findByUser_IdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(UUID userId, Instant now);

    long countByUser_Id(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshSessionEntity s
               set s.revoked = true,
                   s.revokedAt = :revokedAt
             where s.user.id = :userId
               and s.revoked = false
            """)
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}

