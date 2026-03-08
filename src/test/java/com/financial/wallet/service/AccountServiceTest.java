package com.financial.wallet.service;

import com.financial.wallet.domain.Account;
import com.financial.wallet.dto.CreateAccountRequest;
import com.financial.wallet.dto.TransferRequest;
import com.financial.wallet.infra.exception.InsufficientFundsException;
import com.financial.wallet.infra.tenant.TenantContext;
import com.financial.wallet.repository.AccountRepository;
import com.financial.wallet.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AccountService accountService;

    private final UUID TENANT_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();
    private final UUID ACCOUNT_ID = UUID.randomUUID();

    @BeforeEach
    void setupTenant() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ //
    // createAccount //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("createAccount — should persist account with current tenant")
    void createAccount_persistsWithTenant() {
        Account saved = buildAccount(ACCOUNT_ID, BigDecimal.ZERO);
        when(accountRepository.save(any(Account.class))).thenReturn(saved);

        var response = accountService.createAccount(new CreateAccountRequest(USER_ID, BigDecimal.ZERO));

        assertThat(response.tenantId()).isEqualTo(TENANT_ID);
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(accountRepository).save(any(Account.class));
    }

    // ------------------------------------------------------------------ //
    // deposit //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("deposit — should increment balance by the deposited amount")
    void deposit_shouldIncrementBalance() {
        Account account = buildAccount(ACCOUNT_ID, new BigDecimal("100.00"));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);

        var response = accountService.deposit(ACCOUNT_ID, new BigDecimal("50.00"), "test");

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("150.00"));
        verify(transactionRepository).save(any());
    }

    // ------------------------------------------------------------------ //
    // withdraw //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("withdraw — should decrement balance when funds are sufficient")
    void withdraw_withSufficientFunds_shouldDecrement() {
        Account account = buildAccount(ACCOUNT_ID, new BigDecimal("200.00"));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenReturn(account);

        var response = accountService.withdraw(ACCOUNT_ID, new BigDecimal("80.00"), "test");

        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("withdraw — should throw InsufficientFundsException when balance is too low")
    void withdraw_withInsufficientFunds_shouldThrow() {
        Account account = buildAccount(ACCOUNT_ID, new BigDecimal("50.00"));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.withdraw(ACCOUNT_ID, new BigDecimal("100.00"), "test"))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    @DisplayName("withdraw — should reject zero amounts")
    void withdraw_zeroAmount_shouldThrow() {
        Account account = buildAccount(ACCOUNT_ID, new BigDecimal("100.00"));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.withdraw(ACCOUNT_ID, BigDecimal.ZERO, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ //
    // transfer //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("transfer — should debit source and credit target atomically")
    void transfer_shouldBeAtomic() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Account source = buildAccount(sourceId, new BigDecimal("500.00"));
        Account target = buildAccount(targetId, new BigDecimal("100.00"));

        when(accountRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(accountRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.transfer(new TransferRequest(
                sourceId, targetId, new BigDecimal("200.00"), "Test transfer"));

        assertThat(source.getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(target.getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
        verify(transactionRepository, times(2)).save(any());
    }

    // ------------------------------------------------------------------ //
    // Helpers //
    // ------------------------------------------------------------------ //

    /**
     * Builds an Account for testing using reflection to set the id and tenantId
     * fields
     * (which have no setters since they're set only by the builder/JPA).
     */
    private Account buildAccount(UUID id, BigDecimal balance) {
        Account account = Account.builder()
                .tenantId(TENANT_ID)
                .userId(USER_ID)
                .balance(balance)
                .build();
        setField(account, "id", id);
        return account;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
