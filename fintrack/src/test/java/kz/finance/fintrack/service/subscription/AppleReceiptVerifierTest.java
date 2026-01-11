package kz.finance.fintrack.service.subscription;

import com.apple.itunes.storekit.model.Environment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.finance.fintrack.config.AppleIapProperties;
import kz.finance.fintrack.dto.subscription.AppleSk2Snapshot;
import kz.finance.fintrack.exception.FinTrackException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AppleReceiptVerifierTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void prodOk_picksLatestByExpires_andParsesRenewalInfo() throws Exception {
        String json = """
            {
              "status": 0,
              "receipt": { "bundle_id": "kz.finance.fintrack" },
              "latest_receipt_info": [
                {
                  "product_id": "fintrack_pro_month",
                  "transaction_id": "t1",
                  "original_transaction_id": "ot1",
                  "purchase_date_ms": "1000",
                  "expires_date_ms": "2000"
                },
                {
                  "product_id": "fintrack_pro_month",
                  "transaction_id": "t2",
                  "original_transaction_id": "ot1",
                  "purchase_date_ms": "3000",
                  "expires_date_ms": "5000"
                }
              ],
              "pending_renewal_info": [
                {
                  "product_id": "fintrack_pro_month",
                  "auto_renew_status": "1",
                  "grace_period_expires_date_ms": "9000",
                  "is_in_billing_retry_period": "1"
                }
              ]
            }
            """;

        TestVerifier v = verifier("kz.finance.fintrack", 3, 0, json);

        AppleSk2Snapshot snap = v.verifyByReceipt("base64", "fintrack_pro_month");

        assertThat(snap.environment()).isEqualTo(Environment.PRODUCTION);
        assertThat(snap.productId()).isEqualTo("fintrack_pro_month");
        assertThat(snap.transactionId()).isEqualTo("t2");
        assertThat(snap.originalTransactionId()).isEqualTo("ot1");
        assertThat(snap.purchasedAt()).isEqualTo(Instant.ofEpochMilli(3000));
        assertThat(snap.expiresAt()).isEqualTo(Instant.ofEpochMilli(5000));
        assertThat(snap.autoRenew()).isTrue();
        assertThat(snap.graceUntil()).isEqualTo(Instant.ofEpochMilli(9000));
        assertThat(snap.billingRetry()).isTrue();
        assertThat(snap.revoked()).isFalse();
        assertThat(v.calls.get()).isEqualTo(1);
    }

    @Test
    void prod21007_retriesSandbox_andSetsSandboxEnv() throws Exception {
        String prod = """
            { "status": 21007 }
            """;
        String sb = """
            {
              "status": 0,
              "receipt": { "bundle_id": "kz.finance.fintrack" },
              "latest_receipt_info": [
                {
                  "product_id": "fintrack_pro_month",
                  "transaction_id": "t-sb",
                  "original_transaction_id": "ot-sb",
                  "purchase_date_ms": "1000",
                  "expires_date_ms": "2000"
                }
              ]
            }
            """;

        TestVerifier v = verifier("kz.finance.fintrack", 3, 0, prod, sb);

        AppleSk2Snapshot snap = v.verifyByReceipt("base64", "fintrack_pro_month");

        assertThat(snap.environment()).isEqualTo(Environment.SANDBOX);
        assertThat(snap.transactionId()).isEqualTo("t-sb");
        assertThat(v.calls.get()).isEqualTo(2);
    }

    @Test
    void sandboxReturns21008_thenRetriesProduction_andSetsProductionEnv() throws Exception {
        String prod1 = """
            { "status": 21007 }
            """;
        String sb = """
            { "status": 21008 }
            """;
        String prod2 = """
            {
              "status": 0,
              "receipt": { "bundle_id": "kz.finance.fintrack" },
              "latest_receipt_info": [
                {
                  "product_id": "fintrack_pro_month",
                  "transaction_id": "t-prod",
                  "purchase_date_ms": "1",
                  "expires_date_ms": "2"
                }
              ]
            }
            """;

        TestVerifier v = verifier("kz.finance.fintrack", 3, 0, prod1, sb, prod2);

        AppleSk2Snapshot snap = v.verifyByReceipt("base64", "fintrack_pro_month");

        assertThat(snap.environment()).isEqualTo(Environment.PRODUCTION);
        assertThat(snap.transactionId()).isEqualTo("t-prod");
        assertThat(v.calls.get()).isEqualTo(3);
    }

    @Test
    void status21006_expiredIsAccepted() throws Exception {
        String json = """
            {
              "status": 21006,
              "receipt": { "bundle_id": "kz.finance.fintrack" },
              "latest_receipt_info": [
                {
                  "product_id": "fintrack_pro_month",
                  "transaction_id": "t-exp",
                  "purchase_date_ms": "1",
                  "expires_date_ms": "2"
                }
              ]
            }
            """;

        TestVerifier v = verifier("kz.finance.fintrack", 3, 0, json);

        AppleSk2Snapshot snap = v.verifyByReceipt("base64", "fintrack_pro_month");

        assertThat(snap.transactionId()).isEqualTo("t-exp");
    }

    @Test
    void invalidReceiptStatus_throws400() throws Exception {
        String json = """
            { "status": 21002 }
            """;
        TestVerifier v = verifier("kz.finance.fintrack", 3, 0, json);

        Throwable t = catchThrowable(() -> v.verifyByReceipt("base64", "fintrack_pro_month"));
        assertThat(t).isInstanceOf(FinTrackException.class);
        assertThat(t).hasMessageContaining("Invalid receipt");

        Integer code = tryReadStatusCode((FinTrackException) t);
        if (code != null) {
            assertThat(code).isEqualTo(400);
        }
    }

    @Test
    void tempUnavailable21005_retriesAndEventuallySucceeds() throws Exception {
        String first = """
            { "status": 21005 }
            """;
        String second = """
            {
              "status": 0,
              "receipt": { "bundle_id": "kz.finance.fintrack" },
              "latest_receipt_info": [
                {
                  "product_id": "fintrack_pro_month",
                  "transaction_id": "t-ok",
                  "purchase_date_ms": "1",
                  "expires_date_ms": "2"
                }
              ]
            }
            """;

        TestVerifier v = verifier("kz.finance.fintrack", 3, 0, first, second);

        AppleSk2Snapshot snap = v.verifyByReceipt("base64", "fintrack_pro_month");

        assertThat(snap.transactionId()).isEqualTo("t-ok");
        assertThat(v.calls.get()).isEqualTo(2);
    }

    @Test
    void bundleIdMismatch_throws400() throws Exception {
        String json = """
            {
              "status": 0,
              "receipt": { "bundle_id": "other.app" },
              "latest_receipt_info": [
                {
                  "product_id": "fintrack_pro_month",
                  "transaction_id": "t1",
                  "purchase_date_ms": "1",
                  "expires_date_ms": "2"
                }
              ]
            }
            """;
        TestVerifier v = verifier("kz.finance.fintrack", 3, 0, json);

        Throwable t = catchThrowable(() -> v.verifyByReceipt("base64", "fintrack_pro_month"));
        assertThat(t).isInstanceOf(FinTrackException.class);
        assertThat(t).hasMessageContaining("does not belong");

        Integer code = tryReadStatusCode((FinTrackException) t);
        if (code != null) {
            assertThat(code).isEqualTo(400);
        }
    }

    @Test
    void transactionNotFoundForProduct_throws400() throws Exception {
        String json = """
            {
              "status": 0,
              "receipt": { "bundle_id": "kz.finance.fintrack" },
              "latest_receipt_info": [
                {
                  "product_id": "OTHER",
                  "transaction_id": "t1",
                  "purchase_date_ms": "1",
                  "expires_date_ms": "2"
                }
              ]
            }
            """;
        TestVerifier v = verifier("kz.finance.fintrack", 3, 0, json);

        Throwable t = catchThrowable(() -> v.verifyByReceipt("base64", "fintrack_pro_month"));
        assertThat(t).isInstanceOf(FinTrackException.class);
        assertThat(t).hasMessageContaining("Transaction not found");

        Integer code = tryReadStatusCode((FinTrackException) t);
        if (code != null) {
            assertThat(code).isEqualTo(400);
        }
    }

    // ===== helpers =====

    private TestVerifier verifier(String bundleId, int maxAttempts, long backoffMs, String... jsonResponses) throws Exception {
        RestClient rc = mock(RestClient.class); // не используется, т.к. postVerifyReceipt() переопределён

        AppleIapProperties props = new AppleIapProperties(
                bundleId,
                123L,                  // appAppleId (в этом тесте verifyReceipt не зависит от него)
                "issuer",              // issuerId (не используется)
                "key",                 // keyId (не используется)
                new ByteArrayResource("p8".getBytes(StandardCharsets.UTF_8)), // privateKeyP8 (не используется)
                "",                    // sharedSecret
                "fintrack_pro_month,fintrack_pro_year", // allowedProducts (явно)
                Environment.PRODUCTION,
                false,                 // enableOnlineChecks
                List.of(new ByteArrayResource("cert".getBytes(StandardCharsets.UTF_8))), // rootCerts (не используется тут)
                new AppleIapProperties.VerifyReceiptProperties(
                        maxAttempts,
                        backoffMs,
                        512 * 1024
                )
        );

        AppleProductPolicy policy = new AppleProductPolicy(props);

        TestVerifier v = new TestVerifier(rc, om, props, policy);

        for (String j : jsonResponses) {
            v.queue.addLast(om.readTree(j));
        }
        return v;
    }

    private static Integer tryReadStatusCode(FinTrackException ex) {
        try {
            var m = ex.getClass().getMethod("getStatus");
            Object v = m.invoke(ex);
            return (v instanceof Integer i) ? i : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Тестовый наследник: не мокает fluent RestClient chain.
     */
    static final class TestVerifier extends AppleReceiptVerifier {

        final Deque<JsonNode> queue = new ArrayDeque<>();
        final AtomicInteger calls = new AtomicInteger(0);

        TestVerifier(RestClient restClient,
                     ObjectMapper objectMapper,
                     AppleIapProperties props,
                     AppleProductPolicy productPolicy) {
            super(restClient, objectMapper, props, productPolicy);
        }

        @Override
        protected JsonNode postVerifyReceipt(String url, Map<String, Object> body) {
            calls.incrementAndGet();
            JsonNode n = queue.pollFirst();
            if (n == null) throw new AssertionError("No more prepared responses in queue");
            return n;
        }
    }
}
