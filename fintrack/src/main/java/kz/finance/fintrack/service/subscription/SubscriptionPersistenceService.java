package kz.finance.fintrack.service.subscription;

import com.apple.itunes.storekit.model.Environment;
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

    // ===================== PUBLIC API =====================

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
        SubscriptionEntity saved = persistApple(
                user,
                productId,
                txId,
                origTx,
                purchasedAt,
                expiresAt,
                graceUntil,
                autoRenew,
                environment,
                revoked,
                revocationDate,
                now
        );

        deactivateOtherActiveSubscriptions(user, SubscriptionProvider.APPLE, saved.getPurchaseToken(), now);
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
        SubscriptionEntity saved = persistGoogle(
                user,
                productId,
                purchaseToken,
                start,
                expiry,
                graceUntil,
                paymentState,
                cancelReason,
                autoRenewing,
                ackState,
                now
        );

        deactivateOtherActiveSubscriptions(user, SubscriptionProvider.GOOGLE, saved.getPurchaseToken(), now);
        return saved;
    }

    @Transactional(readOnly = true)
    public SubscriptionEntity findBestForUser(UserEntity user, Instant now) {
        var list = subRepo.findTop20ByUserOrderByExpiryDateDesc(user);
        if (list.isEmpty()) return null;
        return pickBestSubscription(list, now);
    }

    @Transactional(readOnly = true)
    public boolean existsGoogleByToken(String token) {
        if (token == null || token.isBlank()) return false;
        return subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.GOOGLE, token).isPresent();
    }

    /**
     * RTDN: обновляем существующую запись по (GOOGLE, purchaseToken).
     * Если записи нет — тихо выходим (RTDN может прийти раньше persist, или запись мигрировали).
     */
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
        String t = requireNonBlank(token, "RTDN token is empty");

        var subOpt = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.GOOGLE, t);
        if (subOpt.isEmpty()) return;

        var sub = subOpt.get();
        if (shouldRevoke) sub.setRevoked(true);

        applyGoogleSnapshot(
                sub,
                sub.getUser(),
                productId,
                t,
                start,
                expiry,
                graceUntil,
                paymentState,
                cancelReason,
                autoRenewing,
                ackState,
                now
        );

        try {
            subRepo.saveAndFlush(sub);
        } catch (DataIntegrityViolationException race) {
            var fresh = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.GOOGLE, t)
                    .orElseThrow(() -> race);

            applyGoogleSnapshot(
                    fresh,
                    fresh.getUser(),
                    productId,
                    t,
                    start,
                    expiry,
                    graceUntil,
                    paymentState,
                    cancelReason,
                    autoRenewing,
                    ackState,
                    now
            );

            subRepo.saveAndFlush(fresh);
        }
    }

    // ===================== GOOGLE =====================

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
        String token = requireNonBlank(purchaseToken, "Google purchaseToken is empty");

        var sub = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.GOOGLE, token)
                .orElseGet(SubscriptionEntity::new);

        applyGoogleSnapshot(
                sub,
                user,
                productId,
                token,
                start,
                expiry,
                graceUntil,
                paymentState,
                cancelReason,
                autoRenewing,
                ackState,
                now
        );

        try {
            return subRepo.saveAndFlush(sub);
        } catch (DataIntegrityViolationException race) {
            var fresh = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.GOOGLE, token)
                    .orElseThrow(() -> race);

            applyGoogleSnapshot(
                    fresh,
                    user,
                    productId,
                    token,
                    start,
                    expiry,
                    graceUntil,
                    paymentState,
                    cancelReason,
                    autoRenewing,
                    ackState,
                    now
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

    // ===================== APPLE =====================

    /**
     * Apple модель:
     * - 1 запись = 1 originalTransactionId
     * - purchaseToken = originalTransactionId (если он есть, иначе txId)
     * - upgrade (month -> year) = обновление productId в этой же записи
     */
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

        // стабильный ключ идентичности подписки
        String purchaseToken = (o != null ? o : t);

        // Находим существующую запись:
        // 1) по appleTransactionId (самый точный)
        // 2) по originalTransactionId (основной ключ для Apple)
        // 3) по purchaseToken (fallback)
        SubscriptionEntity sub = findAppleSubscription(o, t, purchaseToken);

        // upgrade = просто смена productId в этой же записи
        boolean isUpgrade = sub.getId() != null && sub.getOriginalTransactionId() != null
                            && productId != null
                            && sub.getProductId() != null
                            && !productId.equals(sub.getProductId());

        if (isUpgrade) {
            log.info("Apple upgrade detected: originalTxId={}, {} -> {}",
                    sub.getOriginalTransactionId(), sub.getProductId(), productId);
        }

        applyAppleSnapshot(
                sub,
                user,
                productId,
                t,
                o,
                purchasedAt,
                expiresAt,
                graceUntil,
                autoRenew,
                environment,
                revoked,
                revocationDate,
                now
        );

        try {
            return subRepo.saveAndFlush(sub);
        } catch (DataIntegrityViolationException race) {
            // Race: повторно читаем по стабильному ключу purchaseToken
            var fresh = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.APPLE, purchaseToken)
                    .orElseThrow(() -> race);

            applyAppleSnapshot(
                    fresh,
                    user,
                    productId,
                    t,
                    o,
                    purchasedAt,
                    expiresAt,
                    graceUntil,
                    autoRenew,
                    environment,
                    revoked,
                    revocationDate,
                    now
            );

            return subRepo.saveAndFlush(fresh);
        }
    }

    private SubscriptionEntity findAppleSubscription(String origTx, String txId, String purchaseToken) {
        if (txId != null) {
            var byTx = subRepo.findByProviderAndAppleTransactionId(SubscriptionProvider.APPLE, txId);
            if (byTx.isPresent()) return byTx.get();
        }
        if (origTx != null) {
            var byOrig = subRepo.findByProviderAndOriginalTransactionId(SubscriptionProvider.APPLE, origTx);
            if (byOrig.isPresent()) return byOrig.get();
        }
        if (purchaseToken != null) {
            var byToken = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.APPLE, purchaseToken);
            if (byToken.isPresent()) return byToken.get();
        }
        return new SubscriptionEntity();
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

        if (!revoked) {
            Instant currentExpiry = sub.getExpiryDate();
            if (currentExpiry != null && expiresAt != null && !expiresAt.isAfter(currentExpiry)) {
                log.info("Ignoring non-monotonic Apple snapshot: incoming expiry <= current expiry (current={}, incoming={})",
                        currentExpiry, expiresAt);
                return;
            }
            // If currentExpiry != null and incoming expiresAt is null, also ignore to prevent regression.
            if (currentExpiry != null && expiresAt == null) {
                log.info("Ignoring non-monotonic Apple snapshot: incoming expiry is null while current expiry exists (current={})",
                        currentExpiry);
                return;
            }
        }

        sub.setProvider(SubscriptionProvider.APPLE);

        // purchaseToken — стабильный: origTx (если есть) иначе txId
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

        var env = (environment == Environment.SANDBOX)
                ? StoreEnvironment.SANDBOX
                : StoreEnvironment.PRODUCTION;
        sub.setEnvironment(env);

        if (revoked) {
            sub.setRevoked(true);
        }
        if (revocationDate != null && sub.isRevoked()) {
            sub.setRevocationDate(
                    sub.getRevocationDate() == null
                            ? revocationDate
                            : (sub.getRevocationDate().isAfter(revocationDate) ? revocationDate : sub.getRevocationDate())
            );
        }

        sub.setLastVerifiedAt(now);

        var ent = EntitlementResolver.resolve(sub.isRevoked(), sub.getExpiryDate(), sub.getGraceUntil(), now);
        sub.setStatus(SubscriptionStatusMapper.toDb(ent));
        sub.setActive(EntitlementResolver.isActive(ent));
        sub.setPurchaseState(mapUiState(ent, sub.isAutoRenewing()));
    }

    private SubscriptionState mapUiState(EntitlementStatus ent, boolean autoRenew) {
        return switch (ent) {
            case REVOKED -> SubscriptionState.UNKNOWN;
            case ENTITLED, IN_GRACE -> autoRenew ? SubscriptionState.ACTIVE : SubscriptionState.CANCELED;
            case EXPIRED -> SubscriptionState.EXPIRED;
            case NONE -> SubscriptionState.UNKNOWN;
        };
    }

    // ===================== DEACTIVATE =====================

    @Transactional
    public void deactivateOtherActiveSubscriptions(
            UserEntity user,
            SubscriptionProvider provider,
            String keepPurchaseToken,
            Instant now
    ) {
        if (keepPurchaseToken == null || keepPurchaseToken.isBlank()) return;

        subRepo.deactivateOthers(
                user,
                provider,
                keepPurchaseToken,
                SubscriptionStatus.EXPIRED,
                now
        );
    }

    // ===================== PICK BEST =====================

    private SubscriptionEntity pickBestSubscription(List<SubscriptionEntity> list, Instant now) {
        return list.stream()
                .sorted((a, b) -> {
                    int pa = priority(EntitlementResolver.resolve(a.isRevoked(), a.getExpiryDate(), a.getGraceUntil(), now));
                    int pb = priority(EntitlementResolver.resolve(b.isRevoked(), b.getExpiryDate(), b.getGraceUntil(), now));
                    if (pa != pb) return Integer.compare(pa, pb);

                    Instant da = coalesceDate(a);
                    Instant db = coalesceDate(b);
                    return db.compareTo(da);
                })
                .findFirst()
                .orElse(null);
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

    // ===================== UTILS =====================

    private void assertOwnershipOrAssign(SubscriptionEntity sub, UserEntity currentUser) {
        if (sub.getUser() != null && !sub.getUser().getId().equals(currentUser.getId())) {
            throw new FinTrackException(403, "Subscription belongs to another user");
        }
        sub.setUser(currentUser);
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
}
