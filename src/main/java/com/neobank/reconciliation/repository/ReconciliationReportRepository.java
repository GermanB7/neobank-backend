package com.neobank.reconciliation.repository;

import com.neobank.reconciliation.domain.ReconciliationReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReportEntity, UUID> {

    List<ReconciliationReportEntity> findAllByOrderByStartedAtDesc();
}

