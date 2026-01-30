package kz.finance.fintrack.service.subscription;

import kz.finance.fintrack.dto.subscription.EntitlementStatus;
import kz.finance.fintrack.exception.FinTrackException;
import kz.finance.fintrack.model.*;
import kz.finance.fintrack.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPersistenceService {

    private final SubscriptionRepository subRepo;
    @Transactional
    public SubscriptionEntity persistAppleAndDeactivateOthers(
            UserEntity user,
            String productId,
            String txId,
            String origTx,
            Instant purchasedAt,
            Instant expiresAt,
            Instant graceUntil,
            boolean autoRenew,
            com.apple.itunes.storekit.model.Environment environment,
            boolean revoked,
            Instant revocationDate,
            Instant now
    ) {
        var saved = persistApple(
                user, productId, txId, origTx,
                purchasedAt, expiresAt, graceUntil,
                autoRenew, environment, revoked, revocationDate, now
        );

        deactivateOtherActiveSubscriptions(
                user, SubscriptionProvider.APPLE, 
                saved.getPurchaseToken(), 
                saved.getProductId(), 
                saved.getAppleTransactionId(), 
                now
        );
        return saved;
    }

    @Transactional
    public SubscriptionEntity persistGoogleAndDeactivateOthers(
            UserEntity user,
            String productId,
            String purchaseToken,
            Instant start,
            Instant expiry,
            Instant graceUntil,
            Integer paymentState,
            Integer cancelReason,
            boolean autoRenewing,
            Integer ackState,
            Instant now
    ) {
        var saved = persistGoogle(
                user, productId, purchaseToken,
                start, expiry, graceUntil,
                paymentState, cancelReason,
                autoRenewing, ackState, now
        );

        deactivateOtherActiveSubscriptions(
                user, SubscriptionProvider.GOOGLE, 
                saved.getPurchaseToken(), 
                null, // productId не используется для Google
                null, // transactionId не используется для Google
                now
        );
        return saved;
    }

    // ===== GOOGLE (TX) =====
    @Transactional
    public SubscriptionEntity persistGoogle(
            UserEntity user,
            String productId,
            String purchaseToken,
            Instant start,
            Instant expiry,
            Instant graceUntil,
            Integer paymentState,
            Integer cancelReason,
            boolean autoRenewing,
            Integer ackState,
            Instant now
    ) {
        var token = requireNonBlank(purchaseToken, "Google purchaseToken is empty");

        var sub = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.GOOGLE, token)
                .orElseGet(SubscriptionEntity::new);

        applyGoogleSnapshot(
                sub, user, productId, token, start, expiry, graceUntil,
                paymentState, cancelReason, autoRenewing, ackState, now
        );

        try {
            // saveAndFlush нужен, чтобы поймать уникальность здесь, а не на commit
            return subRepo.saveAndFlush(sub);
        } catch (DataIntegrityViolationException race) {
            var fresh = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.GOOGLE, token)
                    .orElseThrow(() -> race);

            applyGoogleSnapshot(
                    fresh, user, productId, token, start, expiry, graceUntil,
                    paymentState, cancelReason, autoRenewing, ackState, now
            );

            return subRepo.saveAndFlush(fresh);
        }
    }

    private void applyGoogleSnapshot(
            SubscriptionEntity sub,
            UserEntity user,
            String productId,
            String purchaseToken,
            Instant start,
            Instant expiry,
            Instant graceUntil,
            Integer paymentState,
            Integer cancelReason,
            boolean autoRenewing,
            Integer ackState,
            Instant now
    ) {
        assertOwnershipOrAssign(sub, user);

        var ent = EntitlementResolver.resolve(sub.isRevoked(), expiry, graceUntil, now);

        sub.setProvider(SubscriptionProvider.GOOGLE);
        sub.setProductId(productId);
        sub.setPurchaseToken(purchaseToken);

        sub.setPurchaseDate(start != null ? start : (sub.getPurchaseDate() != null ? sub.getPurchaseDate() : now));
        sub.setExpiryDate(expiry);
        sub.setGraceUntil(graceUntil);

        sub.setActive(EntitlementResolver.isActive(ent));
        sub.setStatus(SubscriptionStatusMapper.toDb(ent));

        sub.setPurchaseState(mapToSubscriptionState(paymentState));
        sub.setCancelReason(cancelReason);
        sub.setAutoRenewing(autoRenewing);
        sub.setAcknowledgementState(ackState);

        sub.setLastVerifiedAt(now);
    }

    private SubscriptionState mapToSubscriptionState(Integer paymentState) {
        if (paymentState == null) return SubscriptionState.UNKNOWN;
        return SubscriptionState.fromInt(paymentState);
    }

    // ===== APPLE (TX) =====
    @Transactional
    public SubscriptionEntity persistApple(
            UserEntity user,
            String productId,
            String txId,
            String origTx,
            Instant purchasedAt,
            Instant expiresAt,
            Instant graceUntil,
            boolean autoRenew,
            com.apple.itunes.storekit.model.Environment environment,
            boolean revoked,
            Instant revocationDate,
            Instant now
    ) {
        String o = normalize(origTx);
        String t = normalize(txId);

        if (o == null && t == null) {
            throw new FinTrackException(400, "Apple transactionId/originalTransactionId is empty");
        }

        var sub = findAppleSubscription(o, t, productId);
        
        // Проверяем upgrade кейс: если найдена подписка с тем же originalTransactionId, но другим productId
        boolean isUpgrade = sub.getId() != null && o != null && !productId.equals(sub.getProductId());
        if (isUpgrade) {
            log.info("Apple upgrade detected: originalTxId={}, old productId={}, new productId={}, updating existing subscription",
                    o, sub.getProductId(), productId);
            // При upgrade обновляем существующую запись (меняем productId с месячной на годовую)
            // Это позволяет избежать конфликта уникального индекса на originalTransactionId
            // и сохранить связь между подписками через originalTransactionId
        }

        applyAppleSnapshot(
                sub, user, productId, t, o,
                purchasedAt, expiresAt, graceUntil,
                autoRenew, environment, revoked, revocationDate, now
        );

        try {
            return subRepo.saveAndFlush(sub);
        } catch (DataIntegrityViolationException race) {
            // При race condition пытаемся найти существующую запись
            // Учитываем productId для корректной обработки upgrade
            var fresh = refetchApple(o, t, productId);
            if (fresh == null) {
                // Если не найдено - возможно это upgrade, ищем по originalTransactionId
                if (o != null) {
                    fresh = subRepo.findByProviderAndOriginalTransactionId(SubscriptionProvider.APPLE, o).orElse(null);
                    if (fresh != null && !productId.equals(fresh.getProductId())) {
                        log.info("Apple upgrade detected during race: originalTxId={}, old productId={}, new productId={}, updating existing subscription",
                                o, fresh.getProductId(), productId);
                    }
                }
                if (fresh == null) throw race;
            }

            applyAppleSnapshot(
                    fresh, user, productId, t, o,
                    purchasedAt, expiresAt, graceUntil,
                    autoRenew, environment, revoked, revocationDate, now
            );

            return subRepo.saveAndFlush(fresh);
        }
    }

    private SubscriptionEntity refetchApple(String origTx, String txId, String productId) {
        SubscriptionEntity fresh = null;

        // 1. Сначала по transactionId (самый точный)
        if (txId != null) {
            fresh = subRepo.findByProviderAndAppleTransactionId(SubscriptionProvider.APPLE, txId).orElse(null);
            if (fresh != null) return fresh;
        }
        
        // 2. По originalTransactionId + productId (для upgrade)
        if (origTx != null && productId != null) {
            var byOrig = subRepo.findByProviderAndOriginalTransactionId(SubscriptionProvider.APPLE, origTx);
            if (byOrig.isPresent()) {
                var found = byOrig.get();
                if (productId.equals(found.getProductId())) {
                    return found;
                }
                // Если productId отличается - это upgrade, возвращаем существующую запись для обновления
                // Это позволяет избежать конфликта уникального индекса на (provider, original_transaction_id)
                return found;
            }
        }
        
        // 3. Fallback: по originalTransactionId без проверки productId
        // ВАЖНО: только если productId не был передан (для обратной совместимости)
        if (origTx != null && productId == null) {
            fresh = subRepo.findByProviderAndOriginalTransactionId(SubscriptionProvider.APPLE, origTx).orElse(null);
            if (fresh != null) return fresh;
        }
        
        // 4. Fallback: по purchaseToken (только если productId не был передан)
        if (origTx != null && productId == null) {
            fresh = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.APPLE, origTx).orElse(null);
            if (fresh != null) return fresh;
        }
        if (txId != null && productId == null) {
            fresh = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.APPLE, txId).orElse(null);
            if (fresh != null) return fresh;
        }
        
        // Если дошли сюда - это новая подписка (или upgrade, если productId был передан)
        return null;
    }

    private void applyAppleSnapshot(
            SubscriptionEntity sub,
            UserEntity user,
            String productId,
            String txId,
            String origTx,
            Instant purchasedAt,
            Instant expiresAt,
            Instant graceUntil,
            boolean autoRenew,
            com.apple.itunes.storekit.model.Environment environment,
            boolean revoked,
            Instant revocationDate,
            Instant now
    ) {
        assertOwnershipOrAssign(sub, user);

        sub.setProvider(SubscriptionProvider.APPLE);
        
        // purchaseToken — legacy/stable key, берём origTx если есть
        // При upgrade обновляем существующую запись, поэтому purchaseToken остается тем же (originalTransactionId)
        sub.setPurchaseToken(origTx != null ? origTx : txId);
        
        sub.setProductId(productId);
        sub.setAppleTransactionId(txId);
        sub.setOriginalTransactionId(origTx);

        sub.setPurchaseDate(purchasedAt != null ? purchasedAt : (sub.getPurchaseDate() != null ? sub.getPurchaseDate() : now));
        sub.setExpiryDate(expiresAt);
        sub.setGraceUntil(graceUntil);

        sub.setAutoRenewing(autoRenew);
        sub.setAcknowledgementState(0);
        sub.setCancelReason(null);

        // если environment вдруг null — безопаснее считать PRODUCTION (или выбросить 400, на твой выбор)
        var env = environment == null ? com.apple.itunes.storekit.model.Environment.PRODUCTION : environment;

        sub.setEnvironment(
                env == com.apple.itunes.storekit.model.Environment.SANDBOX
                        ? StoreEnvironment.SANDBOX
                        : StoreEnvironment.PRODUCTION
        );

        sub.setRevoked(sub.isRevoked() || revoked);
        // записываем дату, если она пришла и подписка уже revoked (не важно, revoked=true в этом вызове или было ранее)
        if (revocationDate != null && sub.isRevoked()) {
            // если хочешь сохранять самую раннюю/самую позднюю — выбери правило
            sub.setRevocationDate(sub.getRevocationDate() == null
                    ? revocationDate
                    : sub.getRevocationDate().isAfter(revocationDate) ? revocationDate : sub.getRevocationDate());
        }
        sub.setLastVerifiedAt(now);

        var ent = EntitlementResolver.resolve(sub.isRevoked(), sub.getExpiryDate(), sub.getGraceUntil(), now);
        sub.setStatus(SubscriptionStatusMapper.toDb(ent));
        sub.setActive(EntitlementResolver.isActive(ent));
        sub.setPurchaseState(mapUiState(ent, sub.isAutoRenewing()));
    }

    /**
     * Находит существующую Apple подписку или создает новую.
     * 
     * Логика поиска для корректной обработки upgrade (месячная -> годовая):
     * 1. Сначала ищем по transactionId (уникальный для каждой транзакции)
     * 2. Затем ищем по originalTransactionId + productId (комбинация для upgrade)
     * 3. Если не найдено - создаем новую запись
     * 
     * Это позволяет:
     * - При upgrade создавать новую запись для годовой подписки
     * - Сохранять историю обеих подписок (месячная и годовая)
     */
    private SubscriptionEntity findAppleSubscription(String origTx, String txId, String productId) {
        // 1. Сначала ищем по transactionId (самый точный поиск)
        if (txId != null) {
            var byTx = subRepo.findByProviderAndAppleTransactionId(SubscriptionProvider.APPLE, txId);
            if (byTx.isPresent()) return byTx.get();
        }
        
        // 2. Ищем по originalTransactionId + productId (для upgrade кейса)
        // При upgrade месячная и годовая имеют одинаковый originalTransactionId,
        // но разные productId, поэтому нужно искать по комбинации
        if (origTx != null && productId != null) {
            var byOrig = subRepo.findByProviderAndOriginalTransactionId(SubscriptionProvider.APPLE, origTx);
            if (byOrig.isPresent()) {
                var found = byOrig.get();
                // Если productId совпадает - это та же подписка (renewal)
                if (productId.equals(found.getProductId())) {
                    return found;
                }
                // Если productId отличается - это upgrade, возвращаем существующую запись для обновления
                // Это позволяет избежать конфликта уникального индекса на (provider, original_transaction_id)
                return found;
            }
        }
        
        // 3. Fallback: ищем по originalTransactionId без проверки productId
        // (для обратной совместимости, если productId не передан)
        if (origTx != null) {
            var byOrig = subRepo.findByProviderAndOriginalTransactionId(SubscriptionProvider.APPLE, origTx);
            if (byOrig.isPresent()) return byOrig.get();
        }
        
        // 4. Fallback: ищем по purchaseToken
        if (origTx != null) {
            var byToken = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.APPLE, origTx);
            if (byToken.isPresent()) return byToken.get();
        }
        if (txId != null) {
            var byToken = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.APPLE, txId);
            if (byToken.isPresent()) return byToken.get();
        }
        
        return new SubscriptionEntity();
    }

    private SubscriptionState mapUiState(EntitlementStatus ent, boolean autoRenew) {
        return switch (ent) {
            case REVOKED -> SubscriptionState.UNKNOWN;
            case ENTITLED, IN_GRACE -> autoRenew ? SubscriptionState.ACTIVE : SubscriptionState.CANCELED;
            case EXPIRED -> SubscriptionState.EXPIRED;
            case NONE -> SubscriptionState.UNKNOWN;
        };
    }

    private void assertOwnershipOrAssign(SubscriptionEntity sub, UserEntity currentUser) {
        if (sub.getUser() != null && !sub.getUser().getId().equals(currentUser.getId())) {
            throw new FinTrackException(403, "Subscription belongs to another user");
        }
        sub.setUser(currentUser);
    }

    // ===== ME support =====

    @Transactional(readOnly = true)
    public SubscriptionEntity findBestForUser(UserEntity user, Instant now) {
        var list = subRepo.findTop20ByUserOrderByExpiryDateDesc(user);
        if (list.isEmpty()) return null;
        return pickBestSubscription(list, now);
    }

    @Transactional(readOnly = true)
    public boolean existsGoogleByToken(String token) {
        return subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.GOOGLE, token).isPresent();
    }

    // ===== RTDN persist (TX) =====
    @Transactional
    public void persistGoogleRtnd(
            String token,
            boolean shouldRevoke,
            String productId,
            Instant start,
            Instant expiry,
            Instant graceUntil,
            Integer paymentState,
            Integer cancelReason,
            boolean autoRenewing,
            Integer ackState,
            Instant now
    ) {
        var t = requireNonBlank(token, "RTDN token is empty");

        var subOpt = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.GOOGLE, t);
        if (subOpt.isEmpty()) {
            // может быть гонка: RTDN пришёл раньше persistGoogle / запись удалили / миграция
            return;
        }
        var sub = subOpt.get();

        if (shouldRevoke) sub.setRevoked(true);

        applyGoogleSnapshot(
                sub, sub.getUser(),
                productId, t, start, expiry, graceUntil,
                paymentState, cancelReason, autoRenewing, ackState, now
        );

        try {
            subRepo.saveAndFlush(sub);
        } catch (DataIntegrityViolationException race) {
            // рефетчим по тому же уникальному ключу: (GOOGLE, token)
            var fresh = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.GOOGLE, t)
                    .orElseThrow(() -> race);

            applyGoogleSnapshot(
                    fresh, fresh.getUser(),
                    productId, t, start, expiry, graceUntil,
                    paymentState, cancelReason, autoRenewing, ackState, now
            );

            subRepo.saveAndFlush(fresh);
        }
    }

    /**
     * Выбирает лучшую подписку из списка для пользователя.
     * 
     * Приоритеты:
     * 1. Entitlement status (ENTITLED > IN_GRACE > EXPIRED > REVOKED > NONE)
     * 2. При одинаковом статусе: более поздняя дата истечения
     * 3. При одинаковой дате: приоритет годовой подписки над месячной (для upgrade кейса)
     */
    private SubscriptionEntity pickBestSubscription(List<SubscriptionEntity> list, Instant now) {
        return list.stream()
                .sorted((a, b) -> {
                    int pa = priority(EntitlementResolver.resolve(a.isRevoked(), a.getExpiryDate(), a.getGraceUntil(), now));
                    int pb = priority(EntitlementResolver.resolve(b.isRevoked(), b.getExpiryDate(), b.getGraceUntil(), now));
                    if (pa != pb) return Integer.compare(pa, pb);

                    Instant da = coalesceDate(a);
                    Instant db = coalesceDate(b);
                    int dateCompare = db.compareTo(da);
                    if (dateCompare != 0) return dateCompare;
                    
                    // При одинаковой дате: приоритет годовой подписки над месячной
                    // Это важно для upgrade кейса, когда обе подписки активны одновременно
                    int productPriority = compareProductPriority(a.getProductId(), b.getProductId());
                    if (productPriority != 0) return productPriority;
                    
                    return 0;
                })
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Сравнивает приоритет productId.
     * Годовая подписка имеет больший приоритет, чем месячная.
     * 
     * @return отрицательное число, если a < b; положительное, если a > b; 0, если равны
     */
    private int compareProductPriority(String productIdA, String productIdB) {
        if (productIdA == null && productIdB == null) return 0;
        if (productIdA == null) return -1;
        if (productIdB == null) return 1;
        
        boolean aIsYearly = productIdA.contains("_year");
        boolean bIsYearly = productIdB.contains("_year");
        
        if (aIsYearly && !bIsYearly) return 1;  // годовая > месячная
        if (!aIsYearly && bIsYearly) return -1; // месячная < годовая
        return 0; // одинаковый тип
    }

    private int priority(EntitlementStatus s) {
        return switch (s) {
            case ENTITLED -> 0;
            case IN_GRACE -> 1;
            case EXPIRED -> 2;
            case REVOKED -> 3;
            case NONE -> 4;
        };
    }

    private Instant coalesceDate(SubscriptionEntity s) {
        if (s.getGraceUntil() != null) return s.getGraceUntil();
        if (s.getExpiryDate() != null) return s.getExpiryDate();
        return s.getPurchaseDate() != null ? s.getPurchaseDate() : Instant.EPOCH;
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String requireNonBlank(String s, String message) {
        if (s == null || s.isBlank()) throw new FinTrackException(400, message);
        return s;
    }

    /**
     * Деактивирует другие активные подписки того же провайдера для пользователя.
     * 
     * Для Apple: использует productId + transactionId для корректной обработки upgrade.
     * При upgrade месячная и годовая имеют одинаковый purchaseToken (originalTransactionId),
     * поэтому нужно деактивировать по productId или transactionId.
     * 
     * Для Google: использует purchaseToken (уникальный для каждой подписки).
     */
    @Transactional
    public void deactivateOtherActiveSubscriptions(
            UserEntity user,
            SubscriptionProvider provider,
            String keepPurchaseToken,
            String keepProductId,
            String keepTransactionId,
            Instant now
    ) {
        if (keepPurchaseToken == null || keepPurchaseToken.isBlank()) return;

        if (provider == SubscriptionProvider.APPLE && keepProductId != null && keepTransactionId != null) {
            // Для Apple: деактивируем по productId (для upgrade кейса)
            // Деактивируем все подписки, где productId отличается от keepProductId
            // Это важно для upgrade: при переходе месячная -> годовая деактивируем другие подписки
            // (обновленная запись уже имеет новый productId, поэтому не будет деактивирована)
            subRepo.deactivateOthersByProductAndTransaction(
                    user, provider, keepProductId, keepTransactionId, SubscriptionStatus.EXPIRED, now
            );
        } else {
            // Для Google или fallback: деактивируем по purchaseToken
            subRepo.deactivateOthers(user, provider, keepPurchaseToken, SubscriptionStatus.EXPIRED, now);
        }
    }
}
