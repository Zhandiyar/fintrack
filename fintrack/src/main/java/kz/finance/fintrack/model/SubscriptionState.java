package kz.finance.fintrack.model;

public enum SubscriptionState {
    PENDING,          // 0 – ожидается оплата
    ACTIVE,           // 1 – оплата получена, подписка активна
    FREE_TRIAL,       // 2 – в бесплатном пробном периоде
    PENDING_UPGRADE,  // 3 – обновление/смена тарифного плана в процессе
    CANCELED,         // отмена пользователем (auto-renew = false, но expiry > now)
    EXPIRED,          // завершена и срок истёк
    UNKNOWN;          // всё остальное / неизвестное


    public static SubscriptionState fromInt(int paymentState) {
        return switch (paymentState) {
            case 0 -> PENDING;
            case 1 -> ACTIVE;
            case 2 -> FREE_TRIAL;
            case 3 -> PENDING_UPGRADE;
            default -> UNKNOWN;
        };
    }
}
