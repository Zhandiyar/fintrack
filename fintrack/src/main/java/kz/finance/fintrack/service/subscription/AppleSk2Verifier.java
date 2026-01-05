package kz.finance.fintrack.service.subscription;

import com.apple.itunes.storekit.client.APIException;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.LastTransactionsItem;
import com.apple.itunes.storekit.model.StatusResponse;
import com.apple.itunes.storekit.model.SubscriptionGroupIdentifierItem;
import com.apple.itunes.storekit.model.TransactionInfoResponse;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import jakarta.annotation.PostConstruct;
import kz.finance.fintrack.config.AppleStoreKitSk2Config.AppleSk2Clients;
import kz.finance.fintrack.dto.subscription.AppleSk2Snapshot;
import kz.finance.fintrack.dto.subscription.EntitlementStatus;
import kz.finance.fintrack.utils.AppleTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppleSk2Verifier {

    private final AppleSk2Clients sk2;

    @Value("${apple.allowed-products:fintrack_pro_month,fintrack_pro_year}")
    private String allowedProductsCsv;

    private Set<String> allowedProducts;

    @PostConstruct
    void init() {
        allowedProducts = Arrays.stream(allowedProductsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public AppleSk2Snapshot verifyByTransactionId(String transactionId, String expectedProductId) {
        validateProduct(expectedProductId);

        TxEnv txEnv = getTransactionInfoWithFallback(transactionId);

        Environment env = txEnv.env();
        SignedDataVerifier verifier = sk2.verifier(env);

        JWSTransactionDecodedPayload tx = decodeTx(verifier, txEnv.txInfo().getSignedTransactionInfo());

        if (!Objects.equals(tx.getProductId(), expectedProductId)) {
            throw new IllegalArgumentException("Apple productId mismatch");
        }

        Instant purchase = AppleTime.msToInstant(tx.getPurchaseDate());
        Instant expiry = AppleTime.msToInstant(tx.getExpiresDate());

        Instant revocationDate = AppleTime.msToInstant(tx.getRevocationDate());
        boolean revoked = revocationDate != null;

        RenewalFacts renewal = fetchRenewalFacts(env, verifier, transactionId, expectedProductId);

        return new AppleSk2Snapshot(
                env,
                expectedProductId,
                tx.getTransactionId(),
                tx.getOriginalTransactionId(),
                purchase,
                expiry,
                renewal.autoRenew(),
                renewal.graceUntil(),
                renewal.billingRetry(),
                revoked,
                revocationDate
        );
    }

    public EntitlementStatus toEntitlement(AppleSk2Snapshot s, Instant now) {
        if (s.revoked()) return EntitlementStatus.REVOKED;
        if (s.expiresAt() == null) return EntitlementStatus.NONE;

        if (s.graceUntil() != null && now.isBefore(s.graceUntil())) return EntitlementStatus.IN_GRACE;
        if (now.isBefore(s.expiresAt())) return EntitlementStatus.ENTITLED;
        return EntitlementStatus.EXPIRED;
    }

    // ---------------- internal ----------------

    private TxEnv getTransactionInfoWithFallback(String transactionId) {
        Environment first = sk2.preferred();
        Environment second = sk2.other(first);

        try {
            return new TxEnv(first, sk2.client(first).getTransactionInfo(transactionId));
        } catch (Exception e) {
            if (isNotFound(e)) {
                try {
                    return new TxEnv(second, sk2.client(second).getTransactionInfo(transactionId));
                } catch (Exception e2) {
                    throw new IllegalArgumentException("Apple getTransactionInfo failed in both envs: " + safeMsg(e2), e2);
                }
            }
            throw new IllegalArgumentException("Apple getTransactionInfo failed: " + safeMsg(e), e);
        }
    }

    /**
     * Вытаскиваем renewal факты из getAllSubscriptionStatuses:
     * - выбираем "best" last transaction по max expiresDate
     * - без повторного decode одного и того же signedTransactionInfo
     */
    private RenewalFacts fetchRenewalFacts(Environment env, SignedDataVerifier verifier, String transactionId, String expectedProductId) {
        try {
            StatusResponse resp = sk2.client(env).getAllSubscriptionStatuses(transactionId, null);
            if (resp == null || resp.getData() == null) return RenewalFacts.empty();

            Candidate best = null;

            for (SubscriptionGroupIdentifierItem group : resp.getData()) {
                if (group == null || group.getLastTransactions() == null) continue;

                for (LastTransactionsItem lt : group.getLastTransactions()) {
                    if (lt == null) continue;

                    String signedTx = lt.getSignedTransactionInfo();
                    if (signedTx == null || signedTx.isBlank()) continue;

                    // decode ровно один раз
                    JWSTransactionDecodedPayload tx;
                    try {
                        tx = verifier.verifyAndDecodeTransaction(signedTx);
                    } catch (Exception ignore) {
                        continue;
                    }

                    if (!expectedProductId.equals(tx.getProductId())) continue;

                    long expiresMs = tx.getExpiresDate() == null ? 0L : tx.getExpiresDate();

                    Candidate c = new Candidate(lt, tx, expiresMs);
                    if (best == null || c.expiresMs > best.expiresMs) {
                        best = c;
                    }
                }
            }

            if (best == null) return RenewalFacts.empty();

            String signedRenewal = best.lt.getSignedRenewalInfo();
            if (signedRenewal == null || signedRenewal.isBlank()) return RenewalFacts.empty();

            JWSRenewalInfoDecodedPayload renewal = verifier.verifyAndDecodeRenewalInfo(signedRenewal);

            boolean autoRenew = boolFromAny(
                    safeInvoke(renewal, "getAutoRenewStatus"),
                    safeInvoke(renewal, "getRawAutoRenewStatus")
            );

            boolean billingRetry = boolFromAny(
                    safeInvoke(renewal, "getIsInBillingRetryPeriod"),
                    safeInvoke(renewal, "getRawIsInBillingRetryPeriod")
            );

            Long graceMs = longFromAny(safeInvoke(renewal, "getGracePeriodExpiresDate"));
            Instant graceUntil = AppleTime.msToInstant(graceMs);

            return new RenewalFacts(autoRenew, graceUntil, billingRetry);

        } catch (Exception ignore) {
            return RenewalFacts.empty();
        }
    }

    private JWSTransactionDecodedPayload decodeTx(SignedDataVerifier verifier, String signedTx) {
        if (signedTx == null || signedTx.isBlank()) {
            throw new IllegalStateException("Apple getTransactionInfo returned empty signedTransactionInfo");
        }
        try {
            return verifier.verifyAndDecodeTransaction(signedTx);
        } catch (VerificationException e) {
            throw new IllegalArgumentException("Apple signedTransactionInfo verification failed", e);
        }
    }

    private void validateProduct(String productId) {
        if (!allowedProducts.contains(productId)) {
            throw new IllegalArgumentException("Unknown productId");
        }
    }

    private static boolean isNotFound(Exception e) {
        if (e instanceof APIException api) return api.getHttpStatusCode() == 404;
        String m = e.getMessage();
        return m != null && m.contains("404");
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return (m == null) ? e.getClass().getSimpleName() : m;
    }

    private static Object safeInvoke(Object target, String method) {
        if (target == null) return null;
        try {
            var m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean boolFromAny(Object... values) {
        for (Object v : values) {
            if (v == null) continue;

            if (v instanceof Boolean b) return b;
            if (v instanceof Number n) return n.intValue() == 1;

            // Enum (например AutoRenewStatus.ON/OFF)
            if (v instanceof Enum<?> e) {
                String name = e.name().trim().toLowerCase();
                if ("on".equals(name) || "true".equals(name) || "1".equals(name)) return true;
                if ("off".equals(name) || "false".equals(name) || "0".equals(name)) return false;
            }

            String s = String.valueOf(v).trim().toLowerCase();
            if ("1".equals(s) || "true".equals(s) || "on".equals(s)) return true;
            if ("0".equals(s) || "false".equals(s) || "off".equals(s)) return false;
        }
        return false;
    }

    private static Long longFromAny(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }


    private record TxEnv(Environment env, TransactionInfoResponse txInfo) {}

    private record RenewalFacts(boolean autoRenew, Instant graceUntil, boolean billingRetry) {
        static RenewalFacts empty() { return new RenewalFacts(false, null, false); }
    }

    private record Candidate(LastTransactionsItem lt, JWSTransactionDecodedPayload tx, long expiresMs) {}
}
