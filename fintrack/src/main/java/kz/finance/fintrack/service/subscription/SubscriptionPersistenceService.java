package kz.finance.fintrack.service.subscription;

import kz.finance.fintrack.dto.subscription.EntitlementStatus;
import kz.finance.fintrack.exception.FinTrackException;
import kz.finance.fintrack.model.*;
import kz.finance.fintrack.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionPersistenceService {

    private final SubscriptionRepository subRepo;

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

        var sub = findAppleSubscription(o, t);

        applyAppleSnapshot(
                sub, user, productId, t, o,
                purchasedAt, expiresAt, graceUntil,
                autoRenew, environment, revoked, revocationDate, now
        );

        try {
            return subRepo.saveAndFlush(sub);
        } catch (DataIntegrityViolationException race) {
            var fresh = refetchApple(o, t);
            if (fresh == null) throw race;

            applyAppleSnapshot(
                    fresh, user, productId, t, o,
                    purchasedAt, expiresAt, graceUntil,
                    autoRenew, environment, revoked, revocationDate, now
            );

            return subRepo.saveAndFlush(fresh);
        }
    }

    private SubscriptionEntity refetchApple(String origTx, String txId) {
        SubscriptionEntity fresh = null;

        if (origTx != null) {
            fresh = subRepo.findByProviderAndOriginalTransactionId(SubscriptionProvider.APPLE, origTx).orElse(null);
        }
        if (fresh == null && txId != null) {
            fresh = subRepo.findByProviderAndAppleTransactionId(SubscriptionProvider.APPLE, txId).orElse(null);
        }
        if (fresh == null && origTx != null) {
            fresh = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.APPLE, origTx).orElse(null);
        }
        if (fresh == null && txId != null) {
            fresh = subRepo.findByProviderAndPurchaseToken(SubscriptionProvider.APPLE, txId).orElse(null);
        }
        return fresh;
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
        sub.setProductId(productId);

        // purchaseToken — legacy/stable key, берём origTx если есть
        sub.setPurchaseToken(origTx != null ? origTx : txId);
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

    private SubscriptionEntity findAppleSubscription(String origTx, String txId) {
        if (origTx != null) {
            var byOrig = subRepo.findByProviderAndOriginalTransactionId(SubscriptionProvider.APPLE, origTx);
            if (byOrig.isPresent()) return byOrig.get();
        }
        if (txId != null) {
            var byTx = subRepo.findByProviderAndAppleTransactionId(SubscriptionProvider.APPLE, txId);
            if (byTx.isPresent()) return byTx.get();
        }
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
