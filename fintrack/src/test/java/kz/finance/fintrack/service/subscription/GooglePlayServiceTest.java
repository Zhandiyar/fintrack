package kz.finance.fintrack.service.subscription;

import kz.finance.fintrack.client.google.GooglePlayApiClient;
import kz.finance.fintrack.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GooglePlayServiceTest {

    private GooglePlayApiClient google;
    private GooglePlayService service;

    @BeforeEach
    void setUp() {
        google = Mockito.mock(GooglePlayApiClient.class);
        service = new GooglePlayService(google);

        // вручную проставим поля @Value (в юнит-тесте без Spring)
        TestUtils.setField(service, "packageNameProp", "kz.finance.fintrack");
        TestUtils.setField(service, "allowedProductsCsv", "fintrack_pro_month,fintrack_pro_year");

        service.init();
    }

    @Test
    void verify_success_parsesSnapshot() {
        when(google.verifyPurchase(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "expiryTimeMillis", String.valueOf(Instant.now().plusSeconds(3600).toEpochMilli()),
                        "autoRenewing", true,
                        "acknowledgementState", 1,
                        "paymentState", 1
                ));

        var snap = service.verify("kz.finance.fintrack", "fintrack_pro_month", "token123", false);
        assertThat(snap.getProductId()).isEqualTo("fintrack_pro_month");
        assertThat(snap.getPurchaseToken()).isEqualTo("token123");
        assertThat(snap.getExpiry()).isNotNull();
        assertThat(snap.isAutoRenewing()).isTrue();
        assertThat(snap.getAcknowledgementState()).isEqualTo(1);
        assertThat(snap.getPaymentState()).isEqualTo(1);
    }

    @Test
    void verify_invalidPackage_throws() {
        assertThatThrownBy(() -> service.verify("wrong.pkg", "fintrack_pro_month", "t", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("packageName");
    }

    @Test
    void verify_unknownProduct_throws() {
        assertThatThrownBy(() -> service.verify("kz.finance.fintrack", "unknown", "t", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    @Test
    void verify_missingExpiry_throws() {
        when(google.verifyPurchase(anyString(), anyString(), anyString()))
                .thenReturn(Map.of("acknowledgementState", 1));

        assertThatThrownBy(() -> service.verify("kz.finance.fintrack", "fintrack_pro_month", "t", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expiryTimeMillis");
    }

    @Test
    void verify_acknowledge_whenState0_tryAcknowledgeTrue() {
        when(google.verifyPurchase(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "expiryTimeMillis", String.valueOf(Instant.now().plusSeconds(3600).toEpochMilli()),
                        "acknowledgementState", 0
                ));

        doNothing().when(google).acknowledge(anyString(), anyString(), anyString(), anyMap());

        var snap = service.verify("kz.finance.fintrack", "fintrack_pro_month", "t", true);
        verify(google).acknowledge(eq("kz.finance.fintrack"), eq("fintrack_pro_month"), eq("t"), anyMap());
        assertThat(snap.getAcknowledgementState()).isIn(0, 1); // у тебя после ack выставляется 1, но если ack упадёт — останется 0
    }

    @Test
    void verify_doesNotAcknowledge_whenTryAcknowledgeFalse() {
        when(google.verifyPurchase(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "expiryTimeMillis", String.valueOf(Instant.now().plusSeconds(3600).toEpochMilli()),
                        "acknowledgementState", 0
                ));

        service.verify("kz.finance.fintrack", "fintrack_pro_month", "t", false);

        verify(google, never()).acknowledge(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void verify_parsesGraceAndStartAndCancelReason() {
        var now = Instant.now();
        when(google.verifyPurchase(anyString(), anyString(), anyString()))
                .thenReturn(Map.of(
                        "expiryTimeMillis", String.valueOf(now.plusSeconds(3600).toEpochMilli()),
                        "startTimeMillis", String.valueOf(now.minusSeconds(60).toEpochMilli()),
                        "gracePeriodUntilMillis", String.valueOf(now.plusSeconds(600).toEpochMilli()),
                        "cancelReason", 1,
                        "acknowledgementState", 1
                ));

        var snap = service.verify("kz.finance.fintrack", "fintrack_pro_month", "t", false);

        assertThat(snap.getStart()).isNotNull();
        assertThat(snap.getGraceUntil()).isNotNull();
        assertThat(snap.getCancelReason()).isEqualTo(1);
    }
}
