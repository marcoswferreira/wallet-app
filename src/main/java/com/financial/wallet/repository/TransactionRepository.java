package com.financial.wallet.repository;

import com.financial.wallet.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findAllByAccountIdOrderByCreatedAtDesc(UUID accountId);

    Page<Transaction> findAllByAccountId(UUID accountId, Pageable pageable);
}
