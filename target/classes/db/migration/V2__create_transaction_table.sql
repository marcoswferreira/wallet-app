-- V2: Create transaction table (immutable double-entry ledger)
CREATE TABLE IF NOT EXISTS transaction
(
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID           NOT NULL,
    account_id  UUID           NOT NULL REFERENCES account (id) ON DELETE RESTRICT,
    type        VARCHAR(10)    NOT NULL,
    amount      DECIMAL(15, 2) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transaction_type CHECK (type IN ('CREDIT', 'DEBIT'))
);

-- Tenant-scoped index
CREATE INDEX idx_transaction_tenant_id ON transaction (tenant_id);

-- Account-level index for history queries
CREATE INDEX idx_transaction_account_id ON transaction (account_id);

-- Composite index for tenant-filtered account history (most common query)
CREATE INDEX idx_transaction_tenant_account ON transaction (tenant_id, account_id, created_at DESC);
