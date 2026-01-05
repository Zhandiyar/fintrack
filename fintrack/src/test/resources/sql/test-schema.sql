-- минимальная users, только чтобы FK работал
create table if not exists users (
    id bigint primary key
);

-- ===== subscriptions =====

create sequence if not exists seq_subscriptions_id start with 1 increment by 50;

create table if not exists subscriptions (
                                             id bigserial not null,
                                             user_id bigint,
                                             product_id varchar(255) not null,
    purchase_token varchar(255) not null,
    purchase_date timestamp without time zone not null,
    expiry_date timestamptz null,
    grace_until timestamptz null,
    active boolean not null,
    purchase_state varchar(255) not null,
    cancel_reason integer,
    auto_renewing boolean not null,
    acknowledgement_state integer not null,

    provider varchar(16) not null default 'GOOGLE',
    original_transaction_id varchar(128),
    revoked boolean not null default false,
    apple_transaction_id varchar(64),
    environment varchar(16),
    revocation_date timestamptz,
    status varchar(16) not null default 'NONE',
    last_verified_at timestamptz not null default now(),

    constraint pk_subscriptions primary key (id),
    constraint fk_subscriptions_user foreign key (user_id) references users(id),
    constraint chk_subscriptions_provider check (provider in ('GOOGLE','APPLE'))
    );

alter table subscriptions
    alter column id set default nextval('seq_subscriptions_id');

create unique index if not exists ux_sub_provider_purchase_token
    on subscriptions(provider, purchase_token);

create index if not exists idx_sub_user_provider_expiry
    on subscriptions(user_id, provider, expiry_date);

create unique index if not exists ux_sub_provider_orig_tx
    on subscriptions(provider, original_transaction_id)
    where original_transaction_id is not null;

CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_provider_apple_tx
    ON subscriptions(provider, apple_transaction_id)
    WHERE apple_transaction_id IS NOT NULL;


-- ===== idempotency =====

create sequence if not exists seq_iap_idempotency_id start with 1 increment by 50;

create table if not exists iap_idempotency (
    id bigint primary key default nextval('seq_iap_idempotency_id'),
    idem_key varchar(128) not null,
    user_id bigint not null,
    provider varchar(16) not null,
    response_json text not null,
    created_at timestamptz not null default now(),

    constraint fk_iap_idem_user foreign key (user_id) references users(id),
    constraint chk_iap_idempotency_provider check (provider in ('GOOGLE','APPLE'))
    );

create unique index if not exists ux_iap_idem_user_provider_key
    on iap_idempotency(user_id, provider, idem_key);

create index if not exists idx_iap_idem_created_at
    on iap_idempotency(created_at);

-- ===== webhook dedup =====

create sequence if not exists seq_iap_webhook_dedup_id start with 1 increment by 50;

create table if not exists iap_webhook_dedup (
    id bigint primary key default nextval('seq_iap_webhook_dedup_id'),
    provider varchar(16) not null,
    event_id varchar(96) not null,
    created_at timestamptz not null default now()
    );

create unique index if not exists uq_iap_webhook_dedup_provider_event
    on iap_webhook_dedup (provider, event_id);

create index if not exists ix_iap_webhook_dedup_created_at
    on iap_webhook_dedup (created_at);
