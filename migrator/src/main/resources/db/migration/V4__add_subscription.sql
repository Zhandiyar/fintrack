CREATE SEQUENCE seq_subscriptions_id
    START WITH 1 INCREMENT BY 50;

CREATE TABLE subscriptions
(
    id                    BIGINT                      NOT NULL,
    user_id               BIGINT,
    product_id            VARCHAR(255)                NOT NULL,
    purchase_token        VARCHAR(255)                NOT NULL,
    purchase_date         TIMESTAMPTZ                 NOT NULL,
    expiry_date           TIMESTAMPTZ                 NOT NULL,
    active                BOOLEAN                     NOT NULL,
    purchase_state        VARCHAR(255)                NOT NULL,
    cancel_reason         INTEGER,
    auto_renewing         BOOLEAN                     NOT NULL,
    acknowledgement_state INTEGER                     NOT NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT FK_SUBSCRIPTIONS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uc_subscriptions_purchasetoken UNIQUE (purchase_token)
);

CREATE INDEX idx_user_expiry ON subscriptions (user_id, expiry_date);

CREATE UNIQUE INDEX ux_purchase_token ON subscriptions (purchase_token);
