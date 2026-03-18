package com.neobank.reconciliation.repository;

import com.neobank.reconciliation.domain.ReconciliationDiscrepancyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationDiscrepancyRepository extends JpaRepository<ReconciliationDiscrepancyEntity, UUID> {

    List<ReconciliationDiscrepancyEntity> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}

