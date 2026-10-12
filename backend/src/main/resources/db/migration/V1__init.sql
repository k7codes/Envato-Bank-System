-- =============================================================================
-- Envato Bank - Initial schema
-- Money is stored as BIGINT in minor units (kurus/cent). No floating point.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Customers
-- PII (email, national id, phone) stored AES-256-GCM encrypted.
-- *_hash columns are HMAC-SHA256 blind indexes that allow equality lookups
-- without decrypting the data at rest.
-- -----------------------------------------------------------------------------
CREATE TABLE customers (
    id                    UUID         PRIMARY KEY,
    customer_no           VARCHAR(24)  NOT NULL,
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    email_enc             VARCHAR(512) NOT NULL,
    email_hash            VARCHAR(64)  NOT NULL,
    national_id_enc       VARCHAR(512) NOT NULL,
    national_id_hash      VARCHAR(64)  NOT NULL,
    phone_enc             VARCHAR(512) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_customers_customer_no   UNIQUE (customer_no),
    CONSTRAINT uq_customers_email_hash    UNIQUE (email_hash),
    CONSTRAINT uq_customers_national_hash UNIQUE (national_id_hash),
    CONSTRAINT chk_customers_role   CHECK (role IN ('CUSTOMER','EMPLOYEE','ADMIN')),
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE','LOCKED','CLOSED'))
);

-- -----------------------------------------------------------------------------
-- Refresh tokens (stored hashed, never in plaintext)
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    customer_id UUID        NOT NULL REFERENCES customers(id),
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_customer ON refresh_tokens(customer_id);

-- -----------------------------------------------------------------------------
-- Accounts
-- -----------------------------------------------------------------------------
CREATE TABLE accounts (
    id            UUID        PRIMARY KEY,
    account_no    VARCHAR(26) NOT NULL,
    customer_id   UUID        NOT NULL REFERENCES customers(id),
    currency      VARCHAR(3)  NOT NULL,
    type          VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    balance_minor BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_accounts_account_no UNIQUE (account_no),
    CONSTRAINT chk_accounts_currency CHECK (currency IN ('TRY','USD','EUR')),
    CONSTRAINT chk_accounts_type     CHECK (type IN ('CHECKING','SAVINGS')),
    CONSTRAINT chk_accounts_status   CHECK (status IN ('ACTIVE','FROZEN','CLOSED'))
);
CREATE INDEX idx_accounts_customer ON accounts(customer_id);

-- -----------------------------------------------------------------------------
-- Transfers
-- -----------------------------------------------------------------------------
CREATE TABLE transfers (
    id                UUID        PRIMARY KEY,
    reference         VARCHAR(32) NOT NULL,
    source_account_id UUID        NOT NULL REFERENCES accounts(id),
    target_account_id UUID        NOT NULL REFERENCES accounts(id),
    amount_minor      BIGINT      NOT NULL,
    currency          VARCHAR(3)  NOT NULL,
    status            VARCHAR(20) NOT NULL,
    idempotency_key   VARCHAR(80) NOT NULL,
    created_by        UUID        NOT NULL REFERENCES customers(id),
    description       VARCHAR(255),
    created_at        TIMESTAMPTZ NOT NULL,
    completed_at      TIMESTAMPTZ,
    CONSTRAINT uq_transfers_reference UNIQUE (reference),
    CONSTRAINT chk_transfers_amount   CHECK (amount_minor > 0),
    CONSTRAINT chk_transfers_status   CHECK (status IN ('COMPLETED','FAILED')),
    CONSTRAINT chk_transfers_distinct CHECK (source_account_id <> target_account_id)
);
CREATE UNIQUE INDEX uq_transfers_idempotency ON transfers(created_by, idempotency_key);
CREATE INDEX idx_transfers_source_created ON transfers(source_account_id, created_at);
CREATE INDEX idx_transfers_created_by     ON transfers(created_by, created_at);

-- -----------------------------------------------------------------------------
-- Ledger entries (double-entry, append-only, immutable)
--   entry_type = DEBIT  : money leaves the account
--   entry_type = CREDIT : money enters the account
-- For every transfer the sum(debits) == sum(credits) per currency.
-- -----------------------------------------------------------------------------
CREATE TABLE ledger_entries (
    id                  BIGSERIAL   PRIMARY KEY,
    transaction_id      UUID        NOT NULL,
    transfer_id         UUID        REFERENCES transfers(id),
    account_id          UUID        NOT NULL REFERENCES accounts(id),
    entry_type          VARCHAR(6)  NOT NULL,
    amount_minor        BIGINT      NOT NULL,
    currency            VARCHAR(3)  NOT NULL,
    balance_after_minor BIGINT      NOT NULL,
    description         VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('DEBIT','CREDIT')),
    CONSTRAINT chk_ledger_amount     CHECK (amount_minor > 0)
);
CREATE INDEX idx_ledger_account_created ON ledger_entries(account_id, created_at);
CREATE INDEX idx_ledger_transaction     ON ledger_entries(transaction_id);

-- -----------------------------------------------------------------------------
-- Audit log (hash-chained, append-only, tamper evident)
--   entry_hash = sha256(previous_hash | canonical(record fields))
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id            BIGSERIAL   PRIMARY KEY,
    occurred_at   TIMESTAMPTZ NOT NULL,
    actor_id      UUID,
    actor_type    VARCHAR(20) NOT NULL,
    action        VARCHAR(64) NOT NULL,
    entity_type   VARCHAR(64),
    entity_id     VARCHAR(64),
    details       TEXT,
    previous_hash VARCHAR(64) NOT NULL,
    entry_hash    VARCHAR(64) NOT NULL
);
CREATE INDEX idx_audit_occurred ON audit_logs(occurred_at);
CREATE INDEX idx_audit_entity   ON audit_logs(entity_type, entity_id);

-- -----------------------------------------------------------------------------
-- Append-only enforcement (blocks UPDATE/DELETE at the database level)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION forbid_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only; % is not allowed', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();

CREATE TRIGGER trg_audit_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION forbid_modification();
