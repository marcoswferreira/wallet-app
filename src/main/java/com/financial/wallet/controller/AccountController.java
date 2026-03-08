package com.financial.wallet.controller;

import com.financial.wallet.dto.AccountResponse;
import com.financial.wallet.dto.CreateAccountRequest;
import com.financial.wallet.dto.MoneyRequest;
import com.financial.wallet.dto.TransferRequest;
import com.financial.wallet.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** Create a new account for the authenticated tenant. */
    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    /** Get current balance for an account. */
    @GetMapping("/accounts/{id}/balance")
    public ResponseEntity<AccountResponse> balance(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getBalance(id));
    }

    /** Deposit money into an account. */
    @PostMapping("/accounts/{id}/deposit")
    public ResponseEntity<AccountResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody MoneyRequest request) {
        return ResponseEntity.ok(accountService.deposit(id, request.amount(), request.description()));
    }

    /** Withdraw money from an account. */
    @PostMapping("/accounts/{id}/withdraw")
    public ResponseEntity<AccountResponse> withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody MoneyRequest request) {
        return ResponseEntity.ok(accountService.withdraw(id, request.amount(), request.description()));
    }

    /** Transfer money between two accounts within the same tenant. */
    @PostMapping("/transfers")
    public ResponseEntity<Void> transfer(@Valid @RequestBody TransferRequest request) {
        accountService.transfer(request);
        return ResponseEntity.noContent().build();
    }
}
