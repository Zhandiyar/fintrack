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
import java.time.LocalDateTime;
import java.time.ZoneId;
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
            if (cached != null) return cached;
        }

        var snap = gp.verify(req.packageName(), req.productId(), req.purchaseToken(), true);
        var now  = Instant.now();
        var ent  = gp.toEntitlement(snap, now);

        UserEntity user = userService.getCurrentUser();
        SubscriptionEntity sub = repo.findByPurchaseToken(req.purchaseToken())
                .orElseGet(SubscriptionEntity::new);

        sub.setUser(user);
        sub.setProductId(snap.getProductId());
        sub.setPurchaseToken(snap.getPurchaseToken());
        sub.setPurchaseDate(snap.getStart() != null ? LocalDateTime.ofInstant(snap.getStart(), ZoneId.systemDefault()) : LocalDateTime.now());
        sub.setExpiryDate(LocalDateTime.ofInstant(snap.getExpiry(), ZoneId.systemDefault()));
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
        UserEntity user = userService.getCurrentUser();
        return repo.findTopByUserOrderByExpiryDateDesc(user)
                .map(s -> {
                    var ent = (s.isActive())
                            ? EntitlementStatus.ENTITLED
                            : (s.getExpiryDate().isAfter(LocalDateTime.now())
                            ? EntitlementStatus.IN_GRACE  // можно хранить grace-флаг отдельным полем
                            : EntitlementStatus.EXPIRED);
                    return new EntitlementResponse(ent.name(),
                            s.getExpiryDate().atZone(ZoneId.systemDefault()).toInstant(),
                            s.getProductId(), s.isAutoRenewing());
                })
                .orElse(new EntitlementResponse(EntitlementStatus.NONE.name(), null, null, false));
    }

    /** RTDN обновления (ниже — парсер). */
    @Transactional
    public void applyRtnd(GoogleWebhookParser.DeveloperNotification n) {
        if (n.subscriptionNotification() == null) return;
        var sn = n.subscriptionNotification();
        String purchaseToken = sn.purchaseToken();
        String productId = sn.subscriptionId();

        // просто пересинхронизируемся с Google
        try {
            var snap = gp.verify(n.packageName(), productId, purchaseToken, true);
            var ent = gp.toEntitlement(snap, Instant.now());

            var sub = repo.findByPurchaseToken(purchaseToken).orElseGet(SubscriptionEntity::new);
            if (sub.getId() == null) sub.setUser(userService.getCurrentUser());
            sub.setProductId(productId);
            sub.setPurchaseToken(purchaseToken);
            sub.setPurchaseDate(snap.getStart() != null ? LocalDateTime.ofInstant(snap.getStart(), ZoneId.systemDefault()) : LocalDateTime.now());
            sub.setExpiryDate(LocalDateTime.ofInstant(snap.getExpiry(), ZoneId.systemDefault()));
            sub.setActive(ent == EntitlementStatus.ENTITLED || ent == EntitlementStatus.IN_GRACE);
            sub.setPurchaseState(mapToSubscriptionState(snap.getPaymentState()));
            sub.setCancelReason(snap.getCancelReason());
            sub.setAutoRenewing(snap.isAutoRenewing());
            sub.setAcknowledgementState(snap.getAcknowledgementState());
            repo.save(sub);
        } catch (Exception e) {
            log.error("RTDN sync error: {}", e.getMessage(), e);
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
