package kz.finance.fintrack.service.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import kz.finance.fintrack.dto.subscription.AppleSk2Snapshot;
import kz.finance.fintrack.dto.subscription.AppleVerifyRequest;
import kz.finance.fintrack.dto.subscription.EntitlementResponse;
import kz.finance.fintrack.dto.subscription.EntitlementStatus;
import kz.finance.fintrack.model.SubscriptionEntity;
import kz.finance.fintrack.model.SubscriptionProvider;
import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.IapIdempotencyRepository;
import kz.finance.fintrack.service.UserService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceAppleTest {

    private static final Instant NOW = Instant.parse("2026-01-02T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void verifyApple_withTransactionId_usesStoreKit2() {
        var idemRepo = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var appleSk2 = mock(AppleSk2Verifier.class);
        var receiptVerifier = mock(AppleReceiptVerifier.class);
        var userService = mock(UserService.class);
        var persistence = mock(SubscriptionPersistenceService.class);
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        var service = new SubscriptionService(idemRepo, FIXED_CLOCK, gp, appleSk2, receiptVerifier, userService, mapper, persistence);

        var user = new UserEntity();
        user.setId(1L);
        when(userService.getCurrentUser()).thenReturn(user);

        var expiry = NOW.plusSeconds(3600);
        var snap = new AppleSk2Snapshot(
                com.apple.itunes.storekit.model.Environment.PRODUCTION,
                "fintrack_pro_month",
                "tx123",
                "origTx123",
                NOW.minusSeconds(60),
                expiry,
                true,
                null,
                false,
                false,
                null
        );

        when(appleSk2.verifyByTransactionId("tx123", "fintrack_pro_month")).thenReturn(snap);

        var saved = new SubscriptionEntity();
        saved.setProductId("fintrack_pro_month");
        saved.setExpiryDate(expiry);
        saved.setAutoRenewing(true);
        saved.setRevoked(false);

        when(persistence.persistApple(eq(user), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any()))
                .thenReturn(saved);

        var req = new AppleVerifyRequest("fintrack_pro_month", "tx123", null, null);
        var res = service.verifyAppleAndSave(req, null);

        assertThat(res.status()).isEqualTo(EntitlementStatus.ENTITLED);
        verify(appleSk2).verifyByTransactionId("tx123", "fintrack_pro_month");
        verifyNoInteractions(receiptVerifier);
    }

    @Test
    void verifyApple_withReceipt_usesReceiptVerifier() {
        var idemRepo = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var appleSk2 = mock(AppleSk2Verifier.class);
        var receiptVerifier = mock(AppleReceiptVerifier.class);
        var userService = mock(UserService.class);
        var persistence = mock(SubscriptionPersistenceService.class);
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        var service = new SubscriptionService(idemRepo, FIXED_CLOCK, gp, appleSk2, receiptVerifier, userService, mapper, persistence);

        var user = new UserEntity();
        user.setId(1L);
        when(userService.getCurrentUser()).thenReturn(user);

        var expiry = NOW.plusSeconds(3600);
        var snap = new AppleSk2Snapshot(
                com.apple.itunes.storekit.model.Environment.PRODUCTION,
                "fintrack_pro_month",
                "tx456",
                "origTx456",
                NOW.minusSeconds(60),
                expiry,
                false,
                null,
                false,
                false,
                null
        );

        when(receiptVerifier.verifyByReceipt("base64receipt", "fintrack_pro_month")).thenReturn(snap);

        var saved = new SubscriptionEntity();
        saved.setProductId("fintrack_pro_month");
        saved.setExpiryDate(expiry);
        saved.setAutoRenewing(false);
        saved.setRevoked(false);

        when(persistence.persistApple(eq(user), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any()))
                .thenReturn(saved);

        var req = new AppleVerifyRequest("fintrack_pro_month", null, null, "base64receipt");
        var res = service.verifyAppleAndSave(req, null);

        assertThat(res.status()).isEqualTo(EntitlementStatus.ENTITLED);
        verify(receiptVerifier).verifyByReceipt("base64receipt", "fintrack_pro_month");
        verifyNoInteractions(appleSk2);
    }

    @Test
    void verifyApple_withIdemKey_returnsCached() throws Exception {
        var idemRepo = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var appleSk2 = mock(AppleSk2Verifier.class);
        var receiptVerifier = mock(AppleReceiptVerifier.class);
        var userService = mock(UserService.class);
        var persistence = mock(SubscriptionPersistenceService.class);
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        var service = new SubscriptionService(idemRepo, FIXED_CLOCK, gp, appleSk2, receiptVerifier, userService, mapper, persistence);

        var user = new UserEntity();
        user.setId(1L);
        when(userService.getCurrentUser()).thenReturn(user);

        var cached = new EntitlementResponse(EntitlementStatus.ENTITLED, NOW.plusSeconds(3600), "fintrack_pro_month", true);
        var json = mapper.writeValueAsString(cached);

        var idemEntity = new kz.finance.fintrack.model.IapIdempotencyEntity();
        idemEntity.setResponseJson(json);

        when(idemRepo.findByUserAndProviderAndIdemKey(eq(user), eq(SubscriptionProvider.APPLE), eq("k1")))
                .thenReturn(Optional.of(idemEntity));

        var req = new AppleVerifyRequest("fintrack_pro_month", "tx123", null, null);
        var res = service.verifyAppleAndSave(req, "k1");

        assertThat(res.status()).isEqualTo(EntitlementStatus.ENTITLED);
        verifyNoInteractions(appleSk2);
        verifyNoInteractions(receiptVerifier);
        verifyNoInteractions(persistence);
    }
}

