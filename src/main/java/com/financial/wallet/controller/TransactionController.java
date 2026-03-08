package com.financial.multitenancy.controller;

import com.financial.multitenancy.dto.TransactionResponse;
import com.financial.multitenancy.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /** Get paginated transaction history for an account (most recent first). */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<Page<TransactionResponse>> history(
            @PathVariable UUID id,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(transactionService.getHistory(id, pageable));
    }
}
