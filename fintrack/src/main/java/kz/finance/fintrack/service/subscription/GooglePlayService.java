package kz.finance.fintrack.service.subscription;

import jakarta.annotation.PostConstruct;
import kz.finance.fintrack.client.google.GooglePlayApiClient;
import kz.finance.fintrack.dto.subscription.EntitlementStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GooglePlayService {

    @Value("${google.package-name:kz.finance.fintrack}")
    private String packageNameProp;

    @Value("${google.allowed-products:fintrack_pro_month,fintrack_pro_year}")
    private String allowedProductsCsv;

    @Value("${google.service-account-path}")
    private String serviceAccountPath;

    private final GooglePlayApiClient google;
    private String cachedAccessToken;
    private Instant tokenExpiry;

    private Set<String> allowedProducts;

    @PostConstruct
    void init() {
        allowedProducts = Arrays.stream(allowedProductsCsv.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }

    /** Проверить подписку и вернуть «снимок» данных Google. */
    public GoogleSnapshot verify(String packageName, String productId, String purchaseToken, boolean tryAcknowledge) {
        validateInput(packageName, productId);

        Map<String, Object> body = google.verifyPurchase(packageName, productId, purchaseToken);
        if (body == null) throw new IllegalStateException("Empty response from Google");

        long expiryMs = Long.parseLong(String.valueOf(body.get("expiryTimeMillis")));
        Instant expiry = Instant.ofEpochMilli(expiryMs);

        boolean autoRenewing = Boolean.TRUE.equals(body.get("autoRenewing"));
        int acknowledgementState = Integer.parseInt(String.valueOf(body.getOrDefault("acknowledgementState", 0)));
        Integer paymentState = (body.get("paymentState") != null) ? Integer.valueOf(body.get("paymentState").toString()) : null;
        Integer cancelReason = (body.get("cancelReason") != null) ? Integer.valueOf(body.get("cancelReason").toString()) : null;

        Instant start = null;
        if (body.get("startTimeMillis") != null) {
            start = Instant.ofEpochMilli(Long.parseLong(body.get("startTimeMillis").toString()));
        }

        // grace period (если есть)
        Instant graceUntil = null;
        if (body.get("gracePeriodUntilMillis") != null) {
            graceUntil = Instant.ofEpochMilli(Long.parseLong(body.get("gracePeriodUntilMillis").toString()));
        }

        // acknowledge при необходимости
        if (tryAcknowledge && acknowledgementState == 0) {
            try {
                google.acknowledge(packageName, productId, purchaseToken, Map.of());
                acknowledgementState = 1;
            } catch (Exception ackErr) {
                log.warn("Acknowledge failed (will retry later): {}", ackErr.getMessage());
            }
        }

        return new GoogleSnapshot(
                productId, purchaseToken, start, expiry, autoRenewing,
                acknowledgementState, paymentState, cancelReason, graceUntil
        );
    }

    private void validateInput(String packageName, String productId) {
        if (!Objects.equals(packageNameProp, packageName)) {
            throw new IllegalArgumentException("Invalid packageName");
        }
        if (!allowedProducts.contains(productId)) {
            throw new IllegalArgumentException("Unknown productId");
        }
    }

    /** Универсальный «снимок» Google-данных. */
    @Getter @AllArgsConstructor
    public static class GoogleSnapshot {
        private final String productId;
        private final String purchaseToken;
        private final Instant start;
        private final Instant expiry;
        private final boolean autoRenewing;
        private final int acknowledgementState;
        private final Integer paymentState;   // 0..3
        private final Integer cancelReason;   // 0..3
        private final Instant graceUntil;     // nullable
    }

    /** Маппинг Google → EntitlementStatus (в тон фронту). */
    public EntitlementStatus toEntitlement(GoogleSnapshot s, Instant now) {
        // отозвана? (REVOKED/REFUNDED/CHARGEBACK) — RTDN REVOKED, но подстрахуемся
        if (s.getCancelReason() != null && s.getCancelReason() == 1 /* system */) {
            return EntitlementStatus.REVOKED;
        }
        // grace period
        if (s.getGraceUntil() != null && now.isBefore(s.getGraceUntil())) {
            return EntitlementStatus.IN_GRACE;
        }
        // активна?
        if (now.isBefore(s.getExpiry())) {
            return EntitlementStatus.ENTITLED;
        }
        return EntitlementStatus.EXPIRED;
    }
}

