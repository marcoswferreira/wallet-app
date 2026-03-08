package com.financial.multitenancy.service;

import com.financial.multitenancy.domain.Account;
import com.financial.multitenancy.domain.Transaction;
import com.financial.multitenancy.domain.TransactionType;
import com.financial.multitenancy.dto.AccountResponse;
import com.financial.multitenancy.dto.CreateAccountRequest;
import com.financial.multitenancy.dto.TransferRequest;
import com.financial.multitenancy.infra.exception.TenantMismatchException;
import com.financial.multitenancy.infra.tenant.TenantContext;
import com.financial.multitenancy.repository.AccountRepository;
import com.financial.multitenancy.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Core financial operations service.
 *
 * <p>
 * Concurrency Strategy:
 * <ul>
 * <li>{@link Account#getVersion()} acts as the Optimistic Lock sentinel.
 * Hibernate checks it on every UPDATE and throws
 * {@link OptimisticLockingFailureException}
 * if another transaction already committed a newer version.</li>
 * <li>{@code @Retryable} intercepts that exception and retries the entire
 * method
 * up to 3 times with 100ms exponential back-off before giving up.</li>
 * </ul>
 *
 * <p>
 * Tenant Isolation:
 * The
 * {@link com.financial.multitenancy.infra.tenant.TenantFilterActivationAspect}
 * activates
 * the Hibernate {@code tenantFilter} before each method, so every query
 * transparently
 * applies {@code WHERE tenant_id = :tenantId}. An additional explicit check is
 * performed
 * in {@link #loadAccount(UUID)} to guard against any edge-case bypass.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    // ------------------------------------------------------------------ //
    // Account Management //
    // ------------------------------------------------------------------ //

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        UUID tenantId = requireTenantId();

        Account account = Account.builder()
                .tenantId(tenantId)
                .userId(request.userId())
                .balance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO)
                .build();

        account = accountRepository.save(account);
        log.info("Account created: {} for tenant: {}", account.getId(), tenantId);
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getBalance(UUID accountId) {
        return toResponse(loadAccount(accountId));
    }

    // ------------------------------------------------------------------ //
    // Financial Operations (with Optimistic Lock retry) //
    // ------------------------------------------------------------------ //

    /**
     * Deposits money into an account.
     * Retried automatically on {@link OptimisticLockingFailureException}
     * (concurrent
     * modification).
     */
    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 15, backoff = @Backoff(delay = 100, multiplier = 2))
    public AccountResponse deposit(UUID accountId, BigDecimal amount, String description) {
        Account account = loadAccount(accountId);
        account.credit(amount);

        Transaction tx = Transaction.builder()
                .tenantId(account.getTenantId())
                .account(account)
                .type(TransactionType.CREDIT)
                .amount(amount)
                .description(description)
                .build();

        transactionRepository.save(tx);
        accountRepository.save(account);

        log.info("Deposit of {} on account {} (tenant {})", amount, accountId, account.getTenantId());
        return toResponse(account);
    }

    /**
     * Withdraws money from an account.
     * Retried automatically on {@link OptimisticLockingFailureException}.
     * Throws
     * {@link com.financial.multitenancy.infra.exception.InsufficientFundsException}
     * if balance is insufficient.
     */
    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 15, backoff = @Backoff(delay = 100, multiplier = 2))
    public AccountResponse withdraw(UUID accountId, BigDecimal amount, String description) {
        Account account = loadAccount(accountId);
        account.debit(amount);

        Transaction tx = Transaction.builder()
                .tenantId(account.getTenantId())
                .account(account)
                .type(TransactionType.DEBIT)
                .amount(amount)
                .description(description)
                .build();

        transactionRepository.save(tx);
        accountRepository.save(account);

        log.info("Withdrawal of {} from account {} (tenant {})", amount, accountId, account.getTenantId());
        return toResponse(account);
    }

    /**
     * Atomically transfers an amount from one account to another within the same
     * tenant.
     * Both accounts are modified in the same DB transaction to guarantee atomicity.
     */
    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 15, backoff = @Backoff(delay = 100, multiplier = 2))
    public void transfer(TransferRequest request) {
        Account source = loadAccount(request.sourceAccountId());
        Account target = loadAccount(request.targetAccountId());

        source.debit(request.amount());
        target.credit(request.amount());

        String desc = request.description() != null ? request.description() : "Transfer";

        transactionRepository.save(Transaction.builder()
                .tenantId(source.getTenantId())
                .account(source)
                .type(TransactionType.DEBIT)
                .amount(request.amount())
                .description(desc + " → " + target.getId())
                .build());

        transactionRepository.save(Transaction.builder()
                .tenantId(target.getTenantId())
                .account(target)
                .type(TransactionType.CREDIT)
                .amount(request.amount())
                .description(desc + " ← " + source.getId())
                .build());

        accountRepository.save(source);
        accountRepository.save(target);

        log.info("Transfer of {} from {} to {} (tenant {})",
                request.amount(), source.getId(), target.getId(), source.getTenantId());
    }

    // ------------------------------------------------------------------ //
    // Helpers //
    // ------------------------------------------------------------------ //

    private Account loadAccount(UUID accountId) {
        UUID tenantId = requireTenantId();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));

        if (!account.getTenantId().equals(tenantId)) {
            throw new EntityNotFoundException("Account not found: " + accountId);
        }
        return account;
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new TenantMismatchException("No tenant context found in current request");
        }
        return tenantId;
    }

    private AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(), a.getTenantId(), a.getUserId(),
                a.getBalance(), a.getVersion(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
