DROP INDEX IF EXISTS ux_purchase_token;
DROP INDEX IF EXISTS idx_user_expiry;

ALTER TABLE subscriptions
    DROP CONSTRAINT IF EXISTS uc_subscriptions_purchasetoken;

-- subscriptions: добавляем поля под Apple/унификацию
ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS provider VARCHAR(16) NOT NULL DEFAULT 'GOOGLE',
    ADD COLUMN IF NOT EXISTS original_transaction_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS grace_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS revoked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS apple_transaction_id varchar(64),
    ADD COLUMN IF NOT EXISTS environment varchar(16),
    ADD COLUMN IF NOT EXISTS revocation_date timestamptz,
    ADD COLUMN IF NOT EXISTS status varchar(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS last_verified_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE subscriptions
    ALTER COLUMN id SET DEFAULT nextval('seq_subscriptions_id');

ALTER TABLE subscriptions
    ADD CONSTRAINT chk_subscriptions_provider
        CHECK (provider IN ('GOOGLE', 'APPLE'));

-- expiry_date делаем nullable (Apple/исторические записи, etc.)
ALTER TABLE subscriptions
    ALTER COLUMN expiry_date DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_provider_purchase_token
    ON subscriptions(provider, purchase_token);

CREATE INDEX IF NOT EXISTS idx_sub_user_provider_expiry
    ON subscriptions(user_id, provider, expiry_date);

CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_provider_orig_tx
    ON subscriptions(provider, original_transaction_id)
    WHERE original_transaction_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_provider_apple_tx
    ON subscriptions(provider, apple_transaction_id)
    WHERE apple_transaction_id IS NOT NULL;

CREATE SEQUENCE IF NOT EXISTS seq_iap_idempotency_id
    START WITH 1 INCREMENT BY 50;

-- идемпотентность
CREATE TABLE IF NOT EXISTS iap_idempotency (
    id BIGINT PRIMARY KEY DEFAULT nextval('seq_iap_idempotency_id'),
    idem_key VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    provider VARCHAR(16) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_iap_idem_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_iap_idempotency_provider CHECK (provider IN ('GOOGLE', 'APPLE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_iap_idem_user_provider_key
    ON iap_idempotency(user_id, provider, idem_key);

CREATE INDEX IF NOT EXISTS idx_iap_idem_created_at
    ON iap_idempotency(created_at);

CREATE SEQUENCE IF NOT EXISTS seq_iap_webhook_dedup_id
    START WITH 1 INCREMENT BY 50;

create table if not exists iap_webhook_dedup
(
    id          bigserial primary key,
    provider    varchar(16) not null,
    event_id    varchar(96) not null,   -- notificationUUID (Apple) / eventId (Google) и т.п.
    created_at  timestamptz not null default now()
    );

create unique index if not exists uq_iap_webhook_dedup_provider_event
    on iap_webhook_dedup (provider, event_id);

create index if not exists ix_iap_webhook_dedup_created_at
    on iap_webhook_dedup (created_at);