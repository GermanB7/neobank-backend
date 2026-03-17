package com.neobank.audit.repository;

import com.neobank.audit.domain.AuditEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {

    @Query("""
            select a from AuditEventEntity a
            where (:eventType is null or a.eventType = :eventType)
              and (:actorUserId is null or a.actorUserId = :actorUserId)
              and (:resourceType is null or a.resourceType = :resourceType)
            order by a.createdAt desc
            """)
    List<AuditEventEntity> search(String eventType, UUID actorUserId, String resourceType, Pageable pageable);
}

