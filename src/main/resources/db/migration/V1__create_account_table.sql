-- V1: Create account table with optimistic locking support
CREATE TABLE IF NOT EXISTS account
(
    id         UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID           NOT NULL,
    user_id    UUID           NOT NULL,
    balance    DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    version    INTEGER        NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

-- Tenant-scoped index (used by Hibernate filter)
CREATE INDEX idx_account_tenant_id ON account (tenant_id);

-- Tenant + user composite index for user-level queries
CREATE INDEX idx_account_tenant_user ON account (tenant_id, user_id);

-- Trigger to auto-update 'updated_at' on any row modification
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_account_updated_at
    BEFORE UPDATE
    ON account
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
