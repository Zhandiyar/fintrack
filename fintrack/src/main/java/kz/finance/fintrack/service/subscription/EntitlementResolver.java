package kz.finance.fintrack.service.subscription;

import kz.finance.fintrack.dto.subscription.EntitlementStatus;

import java.time.Instant;

public final class EntitlementResolver {

    private EntitlementResolver() {}

    public static EntitlementStatus resolve(
            boolean revoked,
            Instant expiry,
            Instant graceUntil,
            Instant now
    ) {
        if (revoked) return EntitlementStatus.REVOKED;
        if (expiry == null) return EntitlementStatus.NONE;

        if (graceUntil != null && now.isBefore(graceUntil)) return EntitlementStatus.IN_GRACE;
        if (now.isBefore(expiry)) return EntitlementStatus.ENTITLED;
        return EntitlementStatus.EXPIRED;
    }

    public static boolean isActive(EntitlementStatus s) {
        return s == EntitlementStatus.ENTITLED || s == EntitlementStatus.IN_GRACE;
    }
}
