package kz.finance.fintrack.service.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.finance.fintrack.repository.IapIdempotencyRepository;
import kz.finance.fintrack.service.UserService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceRtdnTest {

    private static final Instant NOW = Instant.parse("2026-01-02T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String PKG = "kz.finance.fintrack";

    @Test
    void applyGoogleRtnd_unknownToken_doesNothing() {
        var idem = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var apple = mock(AppleSk2Verifier.class);
        var userService = mock(UserService.class);
        var persistence = mock(SubscriptionPersistenceService.class);

        when(gp.expectedPackageName()).thenReturn(PKG);

        var service = new SubscriptionService(
                idem, FIXED_CLOCK, gp, apple, userService, new ObjectMapper(), persistence
        );

        var n = mock(GoogleWebhookParser.DeveloperNotification.class);
        var sn = mock(GoogleWebhookParser.SubscriptionNotification.class);

        when(n.subscriptionNotification()).thenReturn(sn);
        when(n.packageName()).thenReturn(PKG);
        when(sn.purchaseToken()).thenReturn("token-x");
        when(sn.subscriptionId()).thenReturn("fintrack_pro_month");
        when(sn.notificationType()).thenReturn(12);

        when(persistence.existsGoogleByToken("token-x")).thenReturn(false);

        service.applyGoogleRtnd(n);

        verify(gp).expectedPackageName();
        verify(gp, never()).verify(anyString(), anyString(), anyBoolean());

        verify(persistence, never()).persistGoogleRtnd(
                anyString(), anyBoolean(), anyString(),
                any(), any(), any(),
                any(), any(),
                anyBoolean(), any(),
                any()
        );

        verifyNoInteractions(apple, idem, userService);
    }

    @Test
    void applyGoogleRtnd_revokedType_callsPersistWithShouldRevokeTrue() {
        var idem = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var apple = mock(AppleSk2Verifier.class);
        var userService = mock(UserService.class);
        var persistence = mock(SubscriptionPersistenceService.class);

        when(gp.expectedPackageName()).thenReturn(PKG);

        var service = new SubscriptionService(
                idem, FIXED_CLOCK, gp, apple, userService, new ObjectMapper(), persistence
        );

        var n = mock(GoogleWebhookParser.DeveloperNotification.class);
        var sn = mock(GoogleWebhookParser.SubscriptionNotification.class);

        when(n.subscriptionNotification()).thenReturn(sn);
        when(n.packageName()).thenReturn(PKG);
        when(sn.purchaseToken()).thenReturn("token-1");
        when(sn.subscriptionId()).thenReturn("fintrack_pro_month");
        when(sn.notificationType()).thenReturn(12); // revoked/refunded

        when(persistence.existsGoogleByToken("token-1")).thenReturn(true);

        var expiry = Instant.parse("2026-01-10T00:00:00Z");
        when(gp.verify(eq("fintrack_pro_month"), eq("token-1"), eq(true)))
                .thenReturn(new GooglePlayService.GoogleSnapshot(
                        "fintrack_pro_month",
                        "token-1",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        expiry,
                        true,
                        1,
                        1,
                        null,
                        null
                ));

        service.applyGoogleRtnd(n);

        verify(persistence).persistGoogleRtnd(
                eq("token-1"),
                eq(true),
                eq("fintrack_pro_month"),
                any(),          // start
                eq(expiry),     // expiry
                isNull(),       // graceUntil
                eq(1),          // paymentState
                isNull(),       // cancelReason
                eq(true),       // autoRenewing
                eq(1),          // ackState
                eq(NOW)         // now (Clock.fixed)
        );

        verifyNoInteractions(apple, idem, userService);
    }

    @Test
    void applyGoogleRtnd_blankToken_returns() {
        var persistence = mock(SubscriptionPersistenceService.class);
        var gp = mock(GooglePlayService.class);

        var service = new SubscriptionService(
                mock(IapIdempotencyRepository.class),
                FIXED_CLOCK,
                gp,
                mock(AppleSk2Verifier.class),
                mock(UserService.class),
                new ObjectMapper(),
                persistence
        );

        var n = mock(GoogleWebhookParser.DeveloperNotification.class);
        var sn = mock(GoogleWebhookParser.SubscriptionNotification.class);

        when(n.subscriptionNotification()).thenReturn(sn);
        when(sn.purchaseToken()).thenReturn("   "); // early return

        service.applyGoogleRtnd(n);

        verifyNoInteractions(gp);
        verifyNoInteractions(persistence);
    }

    @Test
    void applyGoogleRtnd_gpVerifyThrows_isSwallowed() {
        var gp = mock(GooglePlayService.class);
        var persistence = mock(SubscriptionPersistenceService.class);

        when(gp.expectedPackageName()).thenReturn(PKG);

        var service = new SubscriptionService(
                mock(IapIdempotencyRepository.class),
                FIXED_CLOCK,
                gp,
                mock(AppleSk2Verifier.class),
                mock(UserService.class),
                new ObjectMapper(),
                persistence
        );

        var n = mock(GoogleWebhookParser.DeveloperNotification.class);
        var sn = mock(GoogleWebhookParser.SubscriptionNotification.class);

        when(n.subscriptionNotification()).thenReturn(sn);
        when(n.packageName()).thenReturn(PKG);
        when(sn.purchaseToken()).thenReturn("token-err");
        when(sn.subscriptionId()).thenReturn("fintrack_pro_month");
        when(sn.notificationType()).thenReturn(1);

        when(persistence.existsGoogleByToken("token-err")).thenReturn(true);
        when(gp.verify(anyString(), anyString(), anyBoolean()))
                .thenThrow(new RuntimeException("boom"));

        service.applyGoogleRtnd(n);

        verify(persistence, never()).persistGoogleRtnd(
                any(), anyBoolean(), any(),
                any(), any(), any(),
                any(), any(),
                anyBoolean(), any(),
                any()
        );
    }
}
