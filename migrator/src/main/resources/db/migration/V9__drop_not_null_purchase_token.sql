-- 1) APPLE: purchase_token может быть NULL
ALTER TABLE subscriptions
    ALTER COLUMN purchase_token DROP NOT NULL;

-- 2) Уникальность purchase_token только если он не NULL
DROP INDEX IF EXISTS ux_sub_provider_purchase_token;

CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_provider_purchase_token_nn
    ON subscriptions (provider, purchase_token)
    WHERE purchase_token IS NOT NULL;

-- 3) Не больше одной активной подписки на (user_id, provider)
CREATE UNIQUE INDEX IF NOT EXISTS ux_sub_user_provider_active
    ON subscriptions (user_id, provider)
    WHERE active = true
    AND user_id IS NOT NULL;

-- 4) Статусы: защита целостности данных
ALTER TABLE subscriptions
    ADD CONSTRAINT chk_subscriptions_status
        CHECK (status IN ('NONE', 'ENTITLED', 'IN_GRACE', 'EXPIRED', 'REVOKED'));

-- 5) Быстрый поиск активной подписки
CREATE INDEX IF NOT EXISTS ix_sub_user_provider_active_lookup
    ON subscriptions (user_id, provider)
    WHERE active = true
    AND user_id IS NOT NULL;
