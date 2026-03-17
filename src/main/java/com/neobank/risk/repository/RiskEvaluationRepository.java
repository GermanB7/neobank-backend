package com.neobank.risk.repository;

import com.neobank.risk.domain.RiskEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskEvaluationRepository extends JpaRepository<RiskEvaluationEntity, UUID> {

    Optional<RiskEvaluationEntity> findTopByTransferIdOrderByCreatedAtDesc(UUID transferId);

    List<RiskEvaluationEntity> findBySourceAccountIdOrderByCreatedAtDesc(UUID sourceAccountId);
}
