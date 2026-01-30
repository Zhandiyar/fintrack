package kz.finance.fintrack.service.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.lang.Nullable;
import kz.finance.fintrack.dto.subscription.*;
import kz.finance.fintrack.exception.FinTrackException;
import kz.finance.fintrack.model.*;
import kz.finance.fintrack.repository.IapIdempotencyRepository;
import kz.finance.fintrack.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    private final IapIdempotencyRepository idemRepo;
    private final Clock clock;

    private final GooglePlayService gp;
    private final AppleSk2Verifier appleSk2;
    private final AppleReceiptVerifier appleReceiptVerifier;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    private final SubscriptionPersistenceService persistence;

    // ===== GOOGLE (network outside TX) =====
    public EntitlementResponse verifyGoogleAndSave(GoogleVerifyRequest req, @Nullable String idemKey) {
        var user = userService.getCurrentUser();

        var cached = findIdemCached(user, SubscriptionProvider.GOOGLE, idemKey, req.productId());
        if (cached != null) return cached;

        var snap = gp.verify(req.productId(), req.purchaseToken(), true);
        var now = Instant.now(clock);

        var saved = persistence.persistGoogleAndDeactivateOthers(
                user,
                snap.getProductId(),
                snap.getPurchaseToken(),
                snap.getStart(),
                snap.getExpiry(),
                snap.getGraceUntil(),
                snap.getPaymentState(),
                snap.getCancelReason(),
                snap.isAutoRenewing(),
                snap.getAcknowledgementState(),
                now
        );

        var ent = EntitlementResolver.resolve(saved.isRevoked(), saved.getExpiryDate(), saved.getGraceUntil(), now);
        var response = new EntitlementResponse(ent, saved.getExpiryDate(), saved.getProductId(), saved.isAutoRenewing());
        saveIdem(user, SubscriptionProvider.GOOGLE, idemKey, response);
        log.info(
                "GOOGLE_VERIFY user={} productId={} purchaseToken={} expires={}",
                user.getId(),
                snap.getProductId(),
                snap.getPurchaseToken(),
                snap.getExpiry()
        ); return response;
    }

    // ===== APPLE (network outside TX) =====
    public EntitlementResponse verifyAppleAndSave(AppleVerifyRequest req, @Nullable String idemKey) {
        var user = userService.getCurrentUser();

        var cached = findIdemCached(user, SubscriptionProvider.APPLE, idemKey, req.productId());
        if (cached != null) return cached;

        AppleSk2Snapshot snap;
        if (req.hasTransactionId()) {
            log.debug("Verifying Apple subscription via StoreKit2 transactionId for productId={}", req.productId());
            snap = appleSk2.verifyByTransactionId(req.transactionId(), req.productId());
        } else if (req.hasSignedTx()) {
            snap = appleSk2.verifyBySignedTransaction(req.signedTransactionInfo(), req.productId());
        } else if (req.hasReceipt()) {
            log.debug("Verifying Apple subscription via receipt (fallback) for productId={}", req.productId());
            snap = appleReceiptVerifier.verifyByReceipt(req.appReceipt(), req.productId());
        } else {
            // Сюда в идеале не должны попадать из-за BeanValidation, но на всякий случай:
            throw new FinTrackException(400, "Provide transactionId or appReceipt");
        }
        if (snap.expiresAt() == null) {
            throw new FinTrackException(400, "Not a subscription transaction (missing expiresAt)");
        }

        var now = Instant.now(clock);

        String origTx = normalize(snap.originalTransactionId());
        String txId   = normalize(snap.transactionId());

        if (origTx == null && txId == null) {
            throw new FinTrackException(400, "Apple transaction identifiers are empty");
        }

        var saved = persistence.persistAppleAndDeactivateOthers(
                user,
                snap.productId(),
                txId,
                origTx,
                snap.purchasedAt(),
                snap.expiresAt(),
                snap.graceUntil(),
                snap.autoRenew(),
                snap.environment(),
                snap.revoked(),
                snap.revocationDate(),
                now
        );

        var ent = EntitlementResolver.resolve(saved.isRevoked(), saved.getExpiryDate(), saved.getGraceUntil(), now);
        var response = new EntitlementResponse(ent, saved.getExpiryDate(), saved.getProductId(), saved.isAutoRenewing());

        saveIdem(user, SubscriptionProvider.APPLE, idemKey, response);
        log.info(
                "APPLE_VERIFY user={} product={} tx={} origTx={} env={} expires={}",
                user.getId(),
                snap.productId(),
                snap.transactionId(),
                snap.originalTransactionId(),
                snap.environment(),
                snap.expiresAt()
        );
        return response;
    }

    // ===== ME =====
    public EntitlementResponse myEntitlement() {
        var user = userService.getCurrentUser();
        var now = Instant.now(clock);

        var best = persistence.findBestForUser(user, now);
        if (best == null) return new EntitlementResponse(EntitlementStatus.NONE, null, null, false);

        var ent = EntitlementResolver.resolve(best.isRevoked(), best.getExpiryDate(), best.getGraceUntil(), now);
        return new EntitlementResponse(ent, best.getExpiryDate(), best.getProductId(), best.isAutoRenewing());
    }

    // ===== RTDN (network outside TX) =====
    public void applyGoogleRtnd(GoogleWebhookParser.DeveloperNotification n) {
        if (n.subscriptionNotification() == null) return;

        var sn = n.subscriptionNotification();
        String token = sn.purchaseToken();
        if (token == null || token.isBlank()) return;

        String productId = sn.subscriptionId();
        if (productId == null || productId.isBlank()) return;

        if (!Objects.equals(gp.expectedPackageName(), n.packageName())) {
            log.warn("RTDN ignored: package mismatch. got={} expected={}", n.packageName(), gp.expectedPackageName());
            return;
        }

        if (!persistence.existsGoogleByToken(token)) {
            log.warn("RTDN for unknown token. sku={} token={}", sn.subscriptionId(), token);
            return;
        }
        try {
            var snap = gp.verify(sn.subscriptionId(), token, true);
            boolean shouldRevoke = GoogleRtdnTypes.isRevokedOrRefunded(sn.notificationType());

            persistence.persistGoogleRtnd(
                    token,
                    shouldRevoke,
                    snap.getProductId(),
                    snap.getStart(),
                    snap.getExpiry(),
                    snap.getGraceUntil(),
                    snap.getPaymentState(),
                    snap.getCancelReason(),
                    snap.isAutoRenewing(),
                    snap.getAcknowledgementState(),
                    Instant.now(clock)
            );
        } catch (Exception e) {
            log.error("RTDN sync error: sku={} msg={}", sn.subscriptionId(), e.getMessage(), e);
        }
    }

    // ===== Idempotency =====

    /**
     * Находит кешированный ответ по idempotency key.
     * 
     * ВАЖНО: для корректной обработки upgrade (месячная -> годовая) нужно учитывать productId.
     * При upgrade пользователь может использовать тот же idemKey для разных productId,
     * поэтому проверяем, что кешированный ответ соответствует запрашиваемому productId.
     */
    private EntitlementResponse findIdemCached(UserEntity user, SubscriptionProvider provider, @Nullable String idemKey, @Nullable String productId) {
        if (idemKey == null || idemKey.isBlank()) return null;

        return idemRepo.findByUserAndProviderAndIdemKey(user, provider, idemKey)
                .map(e -> {
                    try {
                        EntitlementResponse cached = objectMapper.readValue(e.getResponseJson(), EntitlementResponse.class);
                        // Проверяем, что кешированный ответ соответствует запрашиваемому productId
                        // Это важно для upgrade кейса: месячная и годовая могут иметь одинаковый idemKey
                        if (productId != null && cached.productId() != null && !productId.equals(cached.productId())) {
                            log.debug("Idempotency cache mismatch: cached productId={}, requested productId={}, ignoring cache", 
                                    cached.productId(), productId);
                            return null;
                        }
                        return cached;
                    } catch (Exception ex) {
                        log.warn("Failed to parse idempotency cached response. idemKey={} provider={}", idemKey, provider, ex);
                        return null;
                    }
                })
                .orElse(null);
    }

    private void saveIdem(UserEntity user, SubscriptionProvider provider, @Nullable String idemKey, EntitlementResponse response) {
        if (idemKey == null || idemKey.isBlank()) return;

        try {
            var json = objectMapper.writeValueAsString(response);

            var rec = new IapIdempotencyEntity();
            rec.setUser(user);
            rec.setProvider(provider);
            rec.setIdemKey(idemKey);
            rec.setResponseJson(json);

            idemRepo.save(rec);
        } catch (DataIntegrityViolationException race) {
            // гонка — норм
        } catch (Exception e) {
            log.warn("Failed to save idempotency record: {}", e.getMessage());
        }
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static final class GoogleRtdnTypes {
        private GoogleRtdnTypes() {}
        public static boolean isRevokedOrRefunded(Integer type) {
            return type != null && (type == 12 || type == 13);
        }
    }
}
