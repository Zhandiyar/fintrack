package kz.finance.fintrack.service.subscription;

import io.micrometer.common.lang.Nullable;
import kz.finance.fintrack.dto.subscription.EntitlementResponse;
import kz.finance.fintrack.dto.subscription.EntitlementStatus;
import kz.finance.fintrack.dto.subscription.VerifyRequest;
import kz.finance.fintrack.model.SubscriptionEntity;
import kz.finance.fintrack.model.SubscriptionState;
import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.SubscriptionRepository;
import kz.finance.fintrack.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository repo;
    private final GooglePlayService gp;
    private final UserService userService;

    // простейшее хранилище идемпотентности (можно заменить на БД)
    private final ConcurrentMap<String, EntitlementResponse> idemCache = new ConcurrentHashMap<>();

    @Transactional
    public EntitlementResponse verifyAndSave(VerifyRequest req, @Nullable String idemKey) {
        if (idemKey != null) {
            var cached = idemCache.get(idemKey);
            if (cached != null) {
                log.info("[IAP] idemHit key={} productId={} user={}", idemKey, req.productId(), userService.getCurrentUser().getId());
                return cached;
            }
        }

        var snap = gp.verify(req.packageName(), req.productId(), req.purchaseToken(), true);
        var now = Instant.now();
        var ent = gp.toEntitlement(snap, now);

        UserEntity user = userService.getCurrentUser();
        SubscriptionEntity sub = repo.findByPurchaseToken(req.purchaseToken())
                .orElseGet(SubscriptionEntity::new);

        sub.setUser(user);
        sub.setProductId(snap.getProductId());
        sub.setPurchaseToken(snap.getPurchaseToken());
        sub.setPurchaseDate(snap.getStart() != null ? snap.getStart() : Instant.now());
        sub.setExpiryDate(snap.getExpiry());
        sub.setActive(ent == EntitlementStatus.ENTITLED || ent == EntitlementStatus.IN_GRACE);
        sub.setPurchaseState(mapToSubscriptionState(snap.getPaymentState()));
        sub.setCancelReason(snap.getCancelReason());
        sub.setAutoRenewing(snap.isAutoRenewing());
        sub.setAcknowledgementState(snap.getAcknowledgementState());

        repo.save(sub);

        var response = new EntitlementResponse(ent.name(), snap.getExpiry(), snap.getProductId(), snap.isAutoRenewing());
        if (idemKey != null) idemCache.putIfAbsent(idemKey, response);
        return response;
    }

    @Transactional(readOnly = true)
    public EntitlementResponse myEntitlement() {
        return repo.findTopByUserOrderByExpiryDateDesc(userService.getCurrentUser())
                .map(s -> {
                    Instant now = Instant.now();
                    Instant expiry = s.getExpiryDate();

                    EntitlementStatus ent;
                    if (s.getCancelReason() != null && s.getCancelReason() == 1) {
                        ent = EntitlementStatus.REVOKED;               // возврат/чарджбэк
                    } else if (now.isBefore(expiry)) {
                        ent = s.isActive() ? EntitlementStatus.ENTITLED // активна
                                : EntitlementStatus.IN_GRACE; // доступ ещё есть, но не активна (по вашей логике)
                    } else {
                        ent = EntitlementStatus.EXPIRED;
                    }

                    // опционально: мягко синхронизировать признак active
                    boolean shouldBeActive = (ent == EntitlementStatus.ENTITLED || ent == EntitlementStatus.IN_GRACE);
                    if (s.isActive() != shouldBeActive) {
                        s.setActive(shouldBeActive);
                        repo.save(s);
                    }

                    return new EntitlementResponse(ent.name(), expiry, s.getProductId(), s.isAutoRenewing());
                })
                .orElse(new EntitlementResponse(EntitlementStatus.NONE.name(), null, null, false));
    }


    /**
     * RTDN обновления (ниже — парсер).
     */
    @Transactional
    public void applyRtnd(GoogleWebhookParser.DeveloperNotification n) {
        if (n.subscriptionNotification() == null) {
            log.warn("⚠️ RTDN without subscriptionNotification: {}", n);
            return;
        }
        var sn = n.subscriptionNotification();
        String token = sn.purchaseToken();
        String maskedToken = token != null && token.length() > 6
                ? token.substring(0, 4) + "..." + token.substring(token.length() - 3)
                : token;

        log.info("📬 RTDN event: package={} type={} sku={} tokenMasked={}",
                n.packageName(), sn.notificationType(), sn.subscriptionId(), maskedToken);

        // просто пересинхронизируемся с Google
        try {
            var snap = gp.verify(n.packageName(), sn.subscriptionId(), token, true);
            var ent = gp.toEntitlement(snap, Instant.now());
            log.debug("✅ Verification result: entitlement={}, expiry={}", ent, snap.getExpiry());


            var sub = repo.findByPurchaseToken(token).orElseGet(SubscriptionEntity::new);
            sub.setProductId(sn.subscriptionId());
            sub.setPurchaseToken(token);
            sub.setPurchaseDate(snap.getStart() != null
                    ? snap.getStart()
                    : (sub.getPurchaseDate() == null ? Instant.now() : sub.getPurchaseDate()));
            sub.setExpiryDate(snap.getExpiry());
            sub.setActive(ent == EntitlementStatus.ENTITLED || ent == EntitlementStatus.IN_GRACE);
            sub.setPurchaseState(mapToSubscriptionState(snap.getPaymentState()));
            sub.setCancelReason(snap.getCancelReason());
            sub.setAutoRenewing(snap.isAutoRenewing());
            sub.setAcknowledgementState(snap.getAcknowledgementState());
            repo.save(sub);
            log.info("💾 Subscription updated: sku={} active={} expiry={}", sn.subscriptionId(), sub.isActive(), sub.getExpiryDate());

        } catch (Exception e) {
            log.error("❌ RTDN sync error: sku={} tokenMasked={} msg={}", sn.subscriptionId(), maskedToken, e.getMessage(), e);
        }
    }

    private SubscriptionState mapToSubscriptionState(Integer paymentState) {
        if (paymentState == null) return SubscriptionState.UNKNOWN;
        return switch (paymentState) {
            case 0 -> SubscriptionState.PENDING;
            case 1 -> SubscriptionState.ACTIVE;
            case 2 -> SubscriptionState.FREE_TRIAL;
            case 3 -> SubscriptionState.PENDING_UPGRADE;
            default -> SubscriptionState.UNKNOWN;
        };
    }
}
