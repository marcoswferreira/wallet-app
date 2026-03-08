package com.financial.multitenancy.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable ledger entry. Represents a single CREDIT or DEBIT movement on an
 * account.
 *
 * <p>
 * Rules:
 * <ul>
 * <li>{@code amount} is always POSITIVE — the {@code type} field determines
 * direction.</li>
 * <li>Records must NEVER be updated or deleted (enforced by
 * {@code @Immutable}).</li>
 * <li>Tenant isolation enforced via {@code @Filter}; the FilterDef lives on
 * {@link Account}.</li>
 * </ul>
 */
@Entity
@Table(name = "transaction")
@Immutable
@Filter(name = "tenantFilter")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10, updatable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected Transaction() {
    }

    private Transaction(Builder builder) {
        this.tenantId = builder.tenantId;
        this.account = builder.account;
        this.type = builder.type;
        this.amount = builder.amount;
        this.description = builder.description;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Account getAccount() {
        return account;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // ------------------------------------------------------------------ //
    // Builder //
    // ------------------------------------------------------------------ //

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID tenantId;
        private Account account;
        private TransactionType type;
        private BigDecimal amount;
        private String description;

        private Builder() {
        }

        public Builder tenantId(UUID v) {
            this.tenantId = v;
            return this;
        }

        public Builder account(Account v) {
            this.account = v;
            return this;
        }

        public Builder type(TransactionType v) {
            this.type = v;
            return this;
        }

        public Builder amount(BigDecimal v) {
            this.amount = v;
            return this;
        }

        public Builder description(String v) {
            this.description = v;
            return this;
        }

        public Transaction build() {
            return new Transaction(this);
        }
    }
}
