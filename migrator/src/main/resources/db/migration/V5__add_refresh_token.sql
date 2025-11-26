CREATE SEQUENCE seq_refresh_tokens_id
    START WITH 1
    INCREMENT BY 50;

CREATE TABLE refresh_tokens
(
    id            BIGINT PRIMARY KEY DEFAULT nextval('seq_refresh_tokens_id'),
    token         VARCHAR(255)      NOT NULL,
    user_id       BIGINT            NOT NULL,
    expires_at    TIMESTAMP         NOT NULL,
    created_at    TIMESTAMP         NOT NULL DEFAULT NOW(),
    revoked       BOOLEAN           NOT NULL DEFAULT FALSE,
    revoked_at    TIMESTAMP,

    CONSTRAINT uc_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

-- Индекс для частых выборок
CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);

-- Индекс по дате истечения (для cron job очистки)
CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens(expires_at);
