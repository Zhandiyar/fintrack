package kz.finance.fintrack.dto.subscription;

import java.time.Instant;

public record EntitlementResponse(
        String status,           // ENTITLED / IN_GRACE / EXPIRED / REVOKED / NONE
        Instant expiryTime,      // может быть null
        String productId,
        boolean autoRenewing
) {}
