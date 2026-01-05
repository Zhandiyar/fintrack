package kz.finance.fintrack.dto.subscription;

import com.apple.itunes.storekit.model.Environment;

import java.time.Instant;

public record AppleSk2Snapshot(
        Environment environment,
        String productId,
        String transactionId,
        String originalTransactionId,
        Instant purchasedAt,
        Instant expiresAt,
        boolean autoRenew,
        Instant graceUntil,
        boolean billingRetry,
        boolean revoked,
        Instant revocationDate
) {}
