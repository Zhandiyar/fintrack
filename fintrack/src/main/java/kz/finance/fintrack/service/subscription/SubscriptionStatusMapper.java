package kz.finance.fintrack.service.subscription;

import kz.finance.fintrack.dto.subscription.EntitlementStatus;
import kz.finance.fintrack.model.SubscriptionStatus;

public final class SubscriptionStatusMapper {

    private SubscriptionStatusMapper() {}

    public static SubscriptionStatus toDb(EntitlementStatus s) {
        return switch (s) {
            case NONE -> SubscriptionStatus.NONE;
            case ENTITLED -> SubscriptionStatus.ENTITLED;
            case IN_GRACE -> SubscriptionStatus.IN_GRACE;
            case EXPIRED -> SubscriptionStatus.EXPIRED;
            case REVOKED -> SubscriptionStatus.REVOKED;
        };
    }
}
