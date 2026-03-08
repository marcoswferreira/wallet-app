package com.financial.multitenancy;

import com.financial.multitenancy.domain.Account;
import com.financial.multitenancy.dto.CreateAccountRequest;
import com.financial.multitenancy.infra.tenant.TenantContext;
import com.financial.multitenancy.repository.AccountRepository;
import com.financial.multitenancy.service.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test — proves that Optimistic Locking + Spring Retry prevents
 * double-spend when N threads attempt to withdraw simultaneously from the same
 * account.
 *
 * <p>
 * Scenario:
 * <ul>
 * <li>Account starts with R$ 1000</li>
 * <li>10 threads each try to withdraw R$ 100 (total: R$ 1000)</li>
 * <li>Expected outcome: exactly 10 successful withdrawals → balance = R$ 0</li>
 * <li>The @Retryable on OptimisticLockException ensures all threads eventually
 * succeed
 * (they don't race — they retry with the updated balance).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentWithdrawTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private static final int THREADS = 10;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal WITHDRAW_AMOUNT = new BigDecimal("100.00");

    private UUID tenantId;
    private UUID accountId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        var response = accountService.createAccount(
                new CreateAccountRequest(UUID.randomUUID(), INITIAL_BALANCE));
        accountId = response.id();
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("concurrent withdrawals — no double-spend, final balance must be 0")
    void concurrentWithdraw_noDoubleSpend() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREADS);

        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                try {
                    // Set tenant context on each worker thread
                    TenantContext.setTenantId(tenantId);
                    startLatch.await(); // all threads start at the same time
                    accountService.withdraw(accountId, WITHDRAW_AMOUNT, "concurrent test");
                    return true;
                } catch (Exception e) {
                    return false;
                } finally {
                    TenantContext.clear();
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown(); // fire!
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long successes = futures.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return false;
            }
        }).count();

        // Re-set for final DB read
        TenantContext.setTenantId(tenantId);
        Account account = accountRepository.findById(accountId).orElseThrow();

        assertThat(successes).isEqualTo(THREADS);
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
