package com.financial.wallet.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a wallet / account per tenant-user pair.
 *
 * <p>
 * Key design decisions:
 * <ul>
 * <li>{@code @Version} enables Optimistic Locking: Hibernate bumps the version
 * on every
 * UPDATE and throws {@link jakarta.persistence.OptimisticLockException} if two
 * concurrent transactions read the same version and both try to commit.</li>
 * <li>{@code @FilterDef} + {@code @Filter} auto-append
 * {@code WHERE tenant_id = :tenantId}
 * to every HQL/JPQL query when the filter is enabled — preventing cross-tenant
 * leakage
 * at the ORM level. Defined once here; {@link Transaction} reuses it via
 * {@code @Filter}.</li>
 * </ul>
 */
@Entity
@Table(name = "account")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class), defaultCondition = "CAST(tenant_id AS VARCHAR) = :tenantId")
@Filter(name = "tenantFilter")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    /**
     * Hibernate Optimistic Locking version column.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Transaction> transactions = new ArrayList<>();

    /** Required by JPA. */
    protected Account() {
    }

    private Account(Builder builder) {
        this.tenantId = builder.tenantId;
        this.userId = builder.userId;
        this.balance = builder.balance != null ? builder.balance : BigDecimal.ZERO;
    }

    // ------------------------------------------------------------------ //
    // Lifecycle Callbacks //
    // ------------------------------------------------------------------ //

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (balance == null)
            balance = BigDecimal.ZERO;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------ //
    // Getters //
    // ------------------------------------------------------------------ //

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Integer getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    // ------------------------------------------------------------------ //
    // Domain Methods //
    // ------------------------------------------------------------------ //

    /**
     * Credits the account balance.
     */
    public void credit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }

    /**
     * Debits the account balance.
     * Throws
     * {@link com.financial.wallet.infra.exception.InsufficientFundsException}
     * if balance would go negative.
     */
    public void debit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new com.financial.wallet.infra.exception.InsufficientFundsException(
                    "Insufficient funds. Available: " + this.balance + ", requested: " + amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    // ------------------------------------------------------------------ //
    // Builder //
    // ------------------------------------------------------------------ //

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID tenantId;
        private UUID userId;
        private BigDecimal balance;

        private Builder() {
        }

        public Builder tenantId(UUID v) {
            this.tenantId = v;
            return this;
        }

        public Builder userId(UUID v) {
            this.userId = v;
            return this;
        }

        public Builder balance(BigDecimal v) {
            this.balance = v;
            return this;
        }

        public Account build() {
            return new Account(this);
        }
    }
}
