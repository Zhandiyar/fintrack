package kz.finance.fintrack.dto.subscription;

public enum EntitlementStatus {
    ENTITLED,      // активна
    IN_GRACE,      // grace period (платёж не прошёл, доступ ещё есть)
    EXPIRED,       // истекла
    REVOKED,       // отозвана/рефанд/бан
    NONE;          // нет подписки
}
