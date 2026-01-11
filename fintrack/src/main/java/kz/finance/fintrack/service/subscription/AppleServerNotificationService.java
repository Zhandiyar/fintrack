package kz.finance.fintrack.service.subscription;

import com.apple.itunes.storekit.model.AutoRenewStatus;
import com.apple.itunes.storekit.model.Data;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import kz.finance.fintrack.config.AppleStoreKitSk2Config.AppleSk2Clients;
import kz.finance.fintrack.dto.subscription.EntitlementStatus;
import kz.finance.fintrack.model.*;
import kz.finance.fintrack.repository.SubscriptionRepository;
import kz.finance.fintrack.utils.AppleTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleServerNotificationService {

    private final AppleSk2Clients sk2;
    private final SubscriptionRepository subRepo;
    private final WebhookDedupService dedup;

    private final AppleProductPolicy productPolicy;

    /**
     * Apple Server Notifications v2: signedPayload.
     * IMPORTANT: доверяем только Apple подписи/серверу.
     */
    @Transactional
    public void handleSignedPayload(String signedPayload) {
        if (signedPayload == null || signedPayload.isBlank()) {
            log.warn("Apple notification: empty signedPayload");
            return;
        }

        VerifiedNotification verified = verifyNotificationWithFallback(signedPayload);
        if (verified == null) return;

        Environment env = verified.env();
        SignedDataVerifier verifier = sk2.verifier(env);
        ResponseBodyV2DecodedPayload payload = verified.payload();

        Data data = payload.getData();
        if (data == null) {
            log.warn("Apple notification has no data. type={} uuid={}",
                    payload.getNotificationType(), payload.getNotificationUUID());
            return;
        }

        JWSTransactionDecodedPayload tx = decodeTransaction(verifier, data.getSignedTransactionInfo());
        JWSRenewalInfoDecodedPayload renewal = decodeRenewal(verifier, data.getSignedRenewalInfo());

        if (tx == null) {
            log.warn("Apple notification without decoded transaction. type={} subtype={} uuid={}",
                    payload.getNotificationType(), payload.getSubtype(), payload.getNotificationUUID());
            return;
        }

        String productId = normalize(tx.getProductId());
        if (!productPolicy.isAllowed(productId)) {
            log.warn("Apple notification for unknown productId={} type={} uuid={}",
                    productId, payload.getNotificationType(), payload.getNotificationUUID());
            return;
        }

        String originalTxId = normalize(tx.getOriginalTransactionId());
        String transactionId = normalize(tx.getTransactionId());
        if (originalTxId == null && transactionId == null) {
            log.warn("Apple tx has no ids. uuid={}", payload.getNotificationUUID());
            return;
        }

        // Dedup: notificationUUID
        String notificationUuid = normalize(payload.getNotificationUUID());
        if (!dedup.acquire(SubscriptionProvider.APPLE, notificationUuid)) {
            log.info("Apple notification deduped: uuid={}", mask(notificationUuid));
            return;
        }

        Instant now = Instant.now();

        Consumer<SubscriptionEntity> updater = sub -> {
            mergeTxFacts(sub, tx, env, now);
            mergeRenewalFacts(sub, renewal);

            EntitlementStatus ent = EntitlementResolver.resolve(
                    sub.isRevoked(),
                    sub.getExpiryDate(),
                    sub.getGraceUntil(),
                    now
            );

            sub.setStatus(SubscriptionStatusMapper.toDb(ent));
            sub.setActive(EntitlementResolver.isActive(ent));
            sub.setPurchaseState(mapUiState(ent, sub.isAutoRenewing()));
        };

        // upsert + retry на уникальном конфликте
        saveAppleWithUniqRetry(originalTxId, transactionId, updater);

        log.info("Apple notification processed: env={} type={} subtype={} productId={} key={} uuid={}",
                env,
                payload.getNotificationType(),
                payload.getSubtype(),
                productId,
                mask(originalTxId != null ? originalTxId : transactionId),
                mask(notificationUuid)
        );
    }

    // ------------------ Upsert with uniq retry ------------------

    private void saveAppleWithUniqRetry(String origTx, String txId, Consumer<SubscriptionEntity> updater) {
        SubscriptionEntity sub = findAppleSubscription(origTx, txId);
        updater.accept(sub);

        try {
            subRepo.saveAndFlush(sub);
            return;
        } catch (DataIntegrityViolationException race) {
            SubscriptionEntity existing = findAppleSubscription(origTx, txId);
            updater.accept(existing);
            subRepo.saveAndFlush(existing);
        }
    }

    // ------------------ monotonic merge ------------------

    private void mergeTxFacts(SubscriptionEntity sub,
                              JWSTransactionDecodedPayload tx,
                              Environment env,
                              Instant now) {

        sub.setProvider(SubscriptionProvider.APPLE);
        sub.setEnvironment(env == Environment.SANDBOX ? StoreEnvironment.SANDBOX : StoreEnvironment.PRODUCTION);

        String originalTxId = normalize(tx.getOriginalTransactionId());
        String transactionId = normalize(tx.getTransactionId());

        if (originalTxId != null) sub.setOriginalTransactionId(originalTxId);
        if (transactionId != null) sub.setAppleTransactionId(transactionId);

        sub.setProductId(normalize(tx.getProductId()));
        sub.setPurchaseToken(originalTxId != null ? originalTxId : transactionId);

        Instant purchaseAt = AppleTime.msToInstant(tx.getPurchaseDate());
        Instant expiresAt = AppleTime.msToInstant(tx.getExpiresDate());
        Instant revokeAt = AppleTime.msToInstant(tx.getRevocationDate());

        // purchaseDate: keep earliest
        if (sub.getPurchaseDate() == null) {
            sub.setPurchaseDate(purchaseAt != null ? purchaseAt : now);
        } else if (purchaseAt != null && purchaseAt.isBefore(sub.getPurchaseDate())) {
            sub.setPurchaseDate(purchaseAt);
        }

        // expiryDate: keep max
        if (expiresAt != null && (sub.getExpiryDate() == null || expiresAt.isAfter(sub.getExpiryDate()))) {
            sub.setExpiryDate(expiresAt);
        }

        // revoked: sticky true
        if (revokeAt != null) {
            sub.setRevoked(true);
            sub.setRevocationDate(revokeAt);
        }

        sub.setLastVerifiedAt(now);
    }

    private void mergeRenewalFacts(SubscriptionEntity sub, JWSRenewalInfoDecodedPayload renewal) {
        if (renewal == null) return;

        boolean autoRenew = isAutoRenewOn(renewal);
        if (sub.isRevoked()) {
            sub.setAutoRenewing(false);
        } else {
            sub.setAutoRenewing(autoRenew);
        }

        Instant graceUntil = AppleTime.msToInstant(renewal.getGracePeriodExpiresDate());
        if (graceUntil != null && (sub.getGraceUntil() == null || graceUntil.isAfter(sub.getGraceUntil()))) {
            sub.setGraceUntil(graceUntil);
        }
    }

    private static boolean isAutoRenewOn(JWSRenewalInfoDecodedPayload renewal) {
        if (renewal == null) return false;

        try {
            AutoRenewStatus st = renewal.getAutoRenewStatus();
            if (st != null) return st == AutoRenewStatus.ON;
        } catch (Exception ignore) {}

        try {
            Integer raw = renewal.getRawAutoRenewStatus();
            if (raw != null) return raw == 1;
        } catch (Exception ignore) {}

        try {
            String s = String.valueOf(renewal.getRawAutoRenewStatus()).trim();
            if ("1".equals(s)) return true;
            if ("0".equals(s)) return false;
        } catch (Exception ignore) {}

        return false;
    }

    // ------------------ verification with env fallback ------------------

    private VerifiedNotification verifyNotificationWithFallback(String signedPayload) {
        Environment first = sk2.preferred();
        Environment second = sk2.other(first);

        try {
            var payload = sk2.verifier(first).verifyAndDecodeNotification(signedPayload);
            return new VerifiedNotification(first, payload);
        } catch (VerificationException e) {
            try {
                var payload = sk2.verifier(second).verifyAndDecodeNotification(signedPayload);
                return new VerifiedNotification(second, payload);
            } catch (VerificationException e2) {
                log.warn("Apple notification verification failed in both envs: {}", safeMsg(e2));
                return null;
            }
        }
    }

    private JWSTransactionDecodedPayload decodeTransaction(SignedDataVerifier verifier, String signedTransactionInfo) {
        if (signedTransactionInfo == null || signedTransactionInfo.isBlank()) return null;
        try {
            return verifier.verifyAndDecodeTransaction(signedTransactionInfo);
        } catch (VerificationException e) {
            log.warn("Apple signedTransactionInfo verification failed: {}", safeMsg(e));
            return null;
        }
    }

    private JWSRenewalInfoDecodedPayload decodeRenewal(SignedDataVerifier verifier, String signedRenewalInfo) {
        if (signedRenewalInfo == null || signedRenewalInfo.isBlank()) return null;
        try {
            return verifier.verifyAndDecodeRenewalInfo(signedRenewalInfo);
        } catch (VerificationException e) {
            log.warn("Apple signedRenewalInfo verification failed: {}", safeMsg(e));
            return null;
        }
    }

    // ------------------ sub lookup ------------------

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

    // ------------------ helpers ------------------

    private SubscriptionState mapUiState(EntitlementStatus ent, boolean autoRenew) {
        return switch (ent) {
            case REVOKED -> SubscriptionState.UNKNOWN;
            case ENTITLED, IN_GRACE -> autoRenew ? SubscriptionState.ACTIVE : SubscriptionState.CANCELED;
            case EXPIRED -> SubscriptionState.EXPIRED;
            case NONE -> SubscriptionState.UNKNOWN;
        };
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return (m == null) ? e.getClass().getSimpleName() : m;
    }

    private static String mask(String s) {
        if (s == null || s.length() < 8) return "***";
        return s.substring(0, 4) + "…" + s.substring(s.length() - 4);
    }

    private record VerifiedNotification(Environment env, ResponseBodyV2DecodedPayload payload) {}
}
