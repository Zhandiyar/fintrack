DROP INDEX IF EXISTS ux_purchase_token;

ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS uc_subscriptions_purchasetoken;

