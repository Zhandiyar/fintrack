package kz.finance.fintrack.service.subscription;

import com.apple.itunes.storekit.model.Environment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.finance.fintrack.config.AppleIapProperties;
import kz.finance.fintrack.dto.subscription.AppleSk2Snapshot;
import kz.finance.fintrack.exception.FinTrackException;
import kz.finance.fintrack.utils.AppleTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleReceiptVerifier {

    private static final String PRODUCTION_URL = "https://buy.itunes.apple.com/verifyReceipt";
    private static final String SANDBOX_URL = "https://sandbox.itunes.apple.com/verifyReceipt";

    // JSON fields
    private static final String F_STATUS = "status";
    private static final String F_LATEST_RECEIPT_INFO = "latest_receipt_info";
    private static final String F_PENDING_RENEWAL_INFO = "pending_renewal_info";
    private static final String F_RECEIPT = "receipt";
    private static final String F_IN_APP = "in_app";

    private static final String F_PRODUCT_ID = "product_id";
    private static final String F_TRANSACTION_ID = "transaction_id";
    private static final String F_ORIGINAL_TRANSACTION_ID = "original_transaction_id";
    private static final String F_PURCHASE_DATE_MS = "purchase_date_ms";
    private static final String F_EXPIRES_DATE_MS = "expires_date_ms";
    private static final String F_CANCELLATION_DATE_MS = "cancellation_date_ms";

    private static final String F_AUTO_RENEW_STATUS = "auto_renew_status";
    private static final String F_GRACE_PERIOD_EXPIRES_DATE_MS = "grace_period_expires_date_ms";
    private static final String F_IS_IN_BILLING_RETRY_PERIOD = "is_in_billing_retry_period";

    private static final String F_BUNDLE_ID = "bundle_id";

    // Apple verifyReceipt statuses
    private static final int STATUS_OK = 0;
    private static final int STATUS_EXPIRED = 21006;
    private static final int STATUS_TEMP_UNAVAILABLE = 21005;
    private static final int STATUS_SANDBOX_RECEIPT = 21007;
    private static final int STATUS_PROD_RECEIPT_TO_SANDBOX = 21008;

    private static final int STATUS_MALFORMED_RECEIPT = 21000;
    private static final int STATUS_INVALID_RECEIPT = 21002;
    private static final int STATUS_AUTH_FAILED = 21003;
    private static final int STATUS_SHARED_SECRET_MISMATCH = 21004;
    private static final int STATUS_NOT_AUTHORIZED = 21010;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final AppleIapProperties props;
    private final AppleProductPolicy productPolicy;

    public AppleSk2Snapshot verifyByReceipt(String receiptBase64, String expectedProductId) {
        productPolicy.requireAllowed(expectedProductId);

        String receipt = normalizeRequired(receiptBase64, "Receipt is required");
        String productId = normalizeRequired(expectedProductId, "ProductId is required");

        int maxSize = props.verifyReceipt().maxReceiptSizeChars();
        if (receipt.length() > maxSize) {
            log.warn("Receipt too large. productId={}, size={}", productId, receipt.length());
            throw new FinTrackException(400, "Receipt size exceeds maximum allowed size");
        }

        VerifyCallResult call = verifyWithEnvironmentFallback(receipt);
        JsonNode response = call.response();
        Environment env = call.environment();

        int status = readStatus(response);

        if (status == STATUS_TEMP_UNAVAILABLE) {
            throw new FinTrackException(503, "Apple receipt service temporarily unavailable");
        }
        if (!(status == STATUS_OK || status == STATUS_EXPIRED)) {
            if (isInvalidReceiptStatus(status)) {
                log.warn("Invalid receipt. productId={}, status={}", productId, status);
                throw new FinTrackException(400, "Invalid receipt");
            }
            log.warn("Receipt verification failed. productId={}, status={}", productId, status);
            throw new FinTrackException(502, "Apple receipt verification failed with status: " + status);
        }

        validateBundleId(response);

        JsonNode tx = findBestTransactionForProduct(response, productId);
        if (tx == null) {
            throw new FinTrackException(400, "Transaction not found for productId: " + productId);
        }

        RenewalInfo renewalInfo = extractRenewalInfo(response, productId);
        return parseTransaction(tx, productId, env, renewalInfo);
    }

    // ===== Verification flow =====

    private VerifyCallResult verifyWithEnvironmentFallback(String receiptBase64) {
        // 1) первый вызов (например, preferredEnvironment из props)
        Environment firstEnv = props.preferredEnvironment() != null
                ? props.preferredEnvironment()
                : Environment.PRODUCTION;

        VerifyCallResult first = callVerify(firstEnv, receiptBase64);
        int st1 = readStatus(first.response());

        // 2) если Apple говорит "не тот эндпоинт" — переключаемся и повторяем
        Environment redirectEnv = redirectByStatus(st1);
        if (redirectEnv != null && redirectEnv != firstEnv) {
            VerifyCallResult second = callVerify(redirectEnv, receiptBase64);
            int st2 = readStatus(second.response());

            // 3) и на всякий случай: если второй тоже "не тот эндпоинт" (редко, но бывает),
            // переключаемся ещё раз
            Environment redirect2 = redirectByStatus(st2);
            if (redirect2 != null && redirect2 != redirectEnv) {
                return callVerify(redirect2, receiptBase64);
            }
            return second;
        }

        return first;
    }

    private VerifyCallResult callVerify(Environment env, String receiptBase64) {
        String url = (env == Environment.SANDBOX) ? SANDBOX_URL : PRODUCTION_URL;
        JsonNode resp = verifyWithRetry(url, receiptBase64);
        return new VerifyCallResult(resp, env);
    }

    private Environment redirectByStatus(int status) {
        return switch (status) {
            case STATUS_SANDBOX_RECEIPT -> Environment.SANDBOX;       // 21007
            case STATUS_PROD_RECEIPT_TO_SANDBOX -> Environment.PRODUCTION; // 21008
            default -> null;
        };
    }

    private JsonNode verifyWithRetry(String url, String receiptBase64) {
        long backoff = Math.max(0L, props.verifyReceipt().initialBackoffMs());
        int maxAttempts = Math.max(1, props.verifyReceipt().maxAttempts());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                JsonNode resp = verifyReceiptOnce(receiptBase64, url);
                int status = readStatus(resp);

                if (status == STATUS_TEMP_UNAVAILABLE && attempt < maxAttempts) {
                    sleepQuietly(backoff);
                    backoff = nextBackoff(backoff);
                    continue;
                }

                return resp;
            } catch (FinTrackException e) {
                if (e.getStatus() == 503 && attempt < maxAttempts) {
                    sleepQuietly(backoff);
                    backoff = nextBackoff(backoff);
                    continue;
                }
                throw e;
            }
        }

        throw new FinTrackException(503, "Apple receipt service temporarily unavailable");
    }

    private long nextBackoff(long current) {
        long next = current <= 0 ? 200 : current * 2;
        return Math.min(next, 2_000);
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode verifyReceiptOnce(String receiptBase64, String url) {
        Map<String, Object> body = new HashMap<>();
        body.put("receipt-data", receiptBase64);
        body.put("exclude-old-transactions", true);

        if (StringUtils.hasText(props.sharedSecret())) {
            body.put("password", props.sharedSecret());
        }

        try {
            JsonNode node = postVerifyReceipt(url, body);
            if (node == null || node.isNull()) {
                throw new FinTrackException(502, "Empty response from Apple verifyReceipt");
            }
            return node;
        } catch (FinTrackException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple verifyReceipt error: {}", e.getMessage(), e);
            throw new FinTrackException(502, "Apple receipt verification failed: " + e.getMessage());
        }
    }

    /**
     * Extracted for testability.
     */
    protected JsonNode postVerifyReceipt(String url, Map<String, Object> body) throws Exception {
        String responseBody = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), (req, res) -> {
                    int code = res.getStatusCode().value();
                    if (code >= 400 && code < 500) throw new FinTrackException(400, "Invalid receipt request");
                    throw new FinTrackException(503, "Apple service temporarily unavailable");
                })
                .body(String.class);

        if (!StringUtils.hasText(responseBody)) {
            throw new FinTrackException(502, "Empty response from Apple verifyReceipt");
        }
        return objectMapper.readTree(responseBody);
    }

    // ===== Parsing / validation =====

    private void validateBundleId(JsonNode response) {
        String expected = props.bundleId();
        if (!StringUtils.hasText(expected)) return;

        JsonNode receipt = response.get(F_RECEIPT);
        String actual = receipt != null ? receipt.path(F_BUNDLE_ID).asText(null) : null;

        if (!StringUtils.hasText(actual) || !Objects.equals(actual, expected)) {
            log.warn("Receipt bundle_id mismatch. expected={} actual={}", expected, actual);
            throw new FinTrackException(400, "Receipt does not belong to this app");
        }
    }

    private boolean isInvalidReceiptStatus(int status) {
        return status == STATUS_INVALID_RECEIPT
               || status == STATUS_MALFORMED_RECEIPT
               || status == STATUS_AUTH_FAILED
               || status == STATUS_SHARED_SECRET_MISMATCH
               || status == STATUS_NOT_AUTHORIZED;
    }

    private int readStatus(JsonNode response) {
        if (response == null || response.isNull()) {
            throw new FinTrackException(502, "Empty response from Apple verifyReceipt");
        }
        JsonNode s = response.get(F_STATUS);
        if (s == null || s.isNull()) {
            throw new FinTrackException(502, "Missing status in Apple verifyReceipt response");
        }
        return s.asInt();
    }

    private JsonNode findBestTransactionForProduct(JsonNode response, String expectedProductId) {
        JsonNode latest = response.get(F_LATEST_RECEIPT_INFO);
        JsonNode best = pickBestFromArray(latest, expectedProductId, F_EXPIRES_DATE_MS, F_PURCHASE_DATE_MS);
        if (best != null) return best;

        JsonNode receipt = response.get(F_RECEIPT);
        if (receipt != null && !receipt.isNull()) {
            JsonNode inApp = receipt.get(F_IN_APP);
            return pickBestFromArray(inApp, expectedProductId, F_PURCHASE_DATE_MS, F_EXPIRES_DATE_MS);
        }
        return null;
    }

    private JsonNode pickBestFromArray(JsonNode arr, String expectedProductId,
                                       String primaryMsField, String secondaryMsField) {
        if (arr == null || !arr.isArray()) return null;

        JsonNode best = null;
        long bestPrimary = Long.MIN_VALUE;
        long bestSecondary = Long.MIN_VALUE;

        for (JsonNode tx : arr) {
            if (tx == null || tx.isNull() || !tx.hasNonNull(F_PRODUCT_ID)) continue;
            if (!Objects.equals(tx.get(F_PRODUCT_ID).asText(), expectedProductId)) continue;

            long primary = extractMsOrZero(tx.get(primaryMsField));
            long secondary = extractMsOrZero(tx.get(secondaryMsField));

            if (best == null || primary > bestPrimary || (primary == bestPrimary && secondary > bestSecondary)) {
                best = tx;
                bestPrimary = primary;
                bestSecondary = secondary;
            }
        }
        return best;
    }

    private long extractMsOrZero(JsonNode node) {
        if (node == null || node.isNull()) return 0L;
        if (node.isNumber()) return node.asLong(0L);
        if (node.isTextual()) {
            try { return Long.parseLong(node.asText()); } catch (Exception ignore) { return 0L; }
        }
        return node.asLong(0L);
    }

    private RenewalInfo extractRenewalInfo(JsonNode response, String expectedProductId) {
        boolean autoRenew = false;
        Instant graceUntil = null;
        boolean billingRetry = false;

        JsonNode pendingRenewal = response.get(F_PENDING_RENEWAL_INFO);
        if (pendingRenewal != null && pendingRenewal.isArray()) {
            for (JsonNode renewal : pendingRenewal) {
                if (renewal == null || renewal.isNull() || !renewal.hasNonNull(F_PRODUCT_ID)) continue;
                if (!Objects.equals(renewal.get(F_PRODUCT_ID).asText(), expectedProductId)) continue;

                autoRenew = renewal.path(F_AUTO_RENEW_STATUS).asInt(0) == 1;

                long graceMs = extractMsOrZero(renewal.get(F_GRACE_PERIOD_EXPIRES_DATE_MS));
                if (graceMs > 0) graceUntil = AppleTime.msToInstant(graceMs);

                billingRetry = parseBool01(renewal.get(F_IS_IN_BILLING_RETRY_PERIOD));
                break;
            }
        }
        return new RenewalInfo(autoRenew, graceUntil, billingRetry);
    }

    private boolean parseBool01(JsonNode node) {
        if (node == null || node.isNull()) return false;
        if (node.isBoolean()) return node.asBoolean(false);
        if (node.isNumber()) return node.asInt(0) == 1;
        if (node.isTextual()) return "1".equals(node.asText());
        return false;
    }

    private AppleSk2Snapshot parseTransaction(JsonNode tx,
                                              String expectedProductId,
                                              Environment environment,
                                              RenewalInfo renewalInfo) {

        String transactionId = extractString(tx, F_TRANSACTION_ID);
        String originalTransactionId = extractString(tx, F_ORIGINAL_TRANSACTION_ID);
        if (!StringUtils.hasText(originalTransactionId)) originalTransactionId = transactionId;

        Instant purchasedAt = toInstantOrNull(tx.get(F_PURCHASE_DATE_MS));
        Instant expiresAt = toInstantOrNull(tx.get(F_EXPIRES_DATE_MS));

        Instant revocationDate = toInstantOrNull(tx.get(F_CANCELLATION_DATE_MS));
        boolean revoked = revocationDate != null;

        boolean autoRenew = renewalInfo != null && renewalInfo.autoRenew();
        Instant graceUntil = renewalInfo != null ? renewalInfo.graceUntil() : null;
        boolean billingRetry = renewalInfo != null && renewalInfo.billingRetry();

        return new AppleSk2Snapshot(
                environment,
                expectedProductId,
                transactionId,
                originalTransactionId,
                purchasedAt,
                expiresAt,
                autoRenew,
                graceUntil,
                billingRetry,
                revoked,
                revocationDate
        );
    }

    private Instant toInstantOrNull(JsonNode msNode) {
        long ms = extractMsOrZero(msNode);
        return ms > 0 ? AppleTime.msToInstant(ms) : null;
    }

    private String extractString(JsonNode node, String fieldName) {
        if (node == null) return null;
        String v = node.path(fieldName).asText(null);
        return StringUtils.hasText(v) ? v : null;
    }

    private String normalizeRequired(String s, String err) {
        if (!StringUtils.hasText(s)) throw new FinTrackException(400, err);
        String t = s.trim();
        if (t.isEmpty()) throw new FinTrackException(400, err);
        return t;
    }

    private record VerifyCallResult(JsonNode response, Environment environment) {}
    private record RenewalInfo(boolean autoRenew, Instant graceUntil, boolean billingRetry) {}
}
