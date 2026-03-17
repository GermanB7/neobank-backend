package com.neobank.accounts.repository;

import com.neobank.accounts.domain.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    List<AccountEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    boolean existsByAccountNumber(String accountNumber);

    @Query(value = "SELECT nextval('account_number_seq')", nativeQuery = true)
    long nextAccountNumberSequenceValue();
}

