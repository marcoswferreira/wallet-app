package com.financial.multitenancy.service;

import com.financial.multitenancy.dto.TransactionResponse;
import com.financial.multitenancy.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getHistory(UUID accountId, Pageable pageable) {
        return transactionRepository
                .findAllByAccountId(accountId, pageable)
                .map(tx -> new TransactionResponse(
                        tx.getId(),
                        tx.getAccount().getId(),
                        tx.getType(),
                        tx.getAmount(),
                        tx.getDescription(),
                        tx.getCreatedAt()));
    }
}
