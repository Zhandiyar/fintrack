package kz.finance.fintrack.service.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.finance.fintrack.dto.subscription.EntitlementResponse;
import kz.finance.fintrack.dto.subscription.EntitlementStatus;
import kz.finance.fintrack.dto.subscription.GoogleVerifyRequest;
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

class SubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-02T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void verifyGoogle_idempotency_returnsCached() throws Exception {
        var idemRepo = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var apple = mock(AppleSk2Verifier.class);
        var userService = mock(UserService.class);
        var persistence = mock(SubscriptionPersistenceService.class);

        var mapper = new ObjectMapper();
        var service = new SubscriptionService(idemRepo, FIXED_CLOCK, gp, apple, userService, mapper, persistence);

        var user = new UserEntity();
        user.setId(1L);
        when(userService.getCurrentUser()).thenReturn(user);

        var cached = new EntitlementResponse(EntitlementStatus.ENTITLED, NOW.plusSeconds(3600), "fintrack_pro_month", true);
        var json = mapper.writeValueAsString(cached);

        var idemEntity = new kz.finance.fintrack.model.IapIdempotencyEntity();
        idemEntity.setResponseJson(json);

        when(idemRepo.findByUserAndProviderAndIdemKey(eq(user), eq(SubscriptionProvider.GOOGLE), eq("k1")))
                .thenReturn(Optional.of(idemEntity));

        var res = service.verifyGoogleAndSave(new GoogleVerifyRequest("t", "fintrack_pro_month"), "k1");

        assertThat(res.status()).isEqualTo(EntitlementStatus.ENTITLED);
        verifyNoInteractions(gp);
        verifyNoInteractions(persistence);
    }

    @Test
    void verifyGoogle_happyPath_callsVerifyAndPersist() {
        var idemRepo = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var apple = mock(AppleSk2Verifier.class);
        var userService = mock(UserService.class);
        var persistence = mock(SubscriptionPersistenceService.class);

        var mapper = new ObjectMapper();
        var service = new SubscriptionService(idemRepo, FIXED_CLOCK, gp, apple, userService, mapper, persistence);

        var user = new UserEntity();
        user.setId(1L);
        when(userService.getCurrentUser()).thenReturn(user);

        var expiry = NOW.plusSeconds(3600);

        var snap = new GooglePlayService.GoogleSnapshot(
                "fintrack_pro_month",
                "token123",
                NOW.minusSeconds(60),
                expiry,
                true,
                1,
                1,
                null,
                null
        );

        when(gp.verify(eq("fintrack_pro_month"), eq("token123"), eq(true)))
                .thenReturn(snap);

        var saved = new kz.finance.fintrack.model.SubscriptionEntity();
        saved.setProductId("fintrack_pro_month");
        saved.setExpiryDate(expiry);
        saved.setAutoRenewing(true);
        saved.setRevoked(false);

        when(persistence.persistGoogle(eq(user), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(saved);

        var res = service.verifyGoogleAndSave(new GoogleVerifyRequest("token123", "fintrack_pro_month"), null);

        assertThat(res.status()).isEqualTo(EntitlementStatus.ENTITLED);
        verify(gp).verify("fintrack_pro_month", "token123", true);
        verify(persistence).persistGoogle(eq(user), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void verifyGoogle_withIdemKey_savesIdempotencyRecord() {
        var idemRepo = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var apple = mock(AppleSk2Verifier.class);
        var userService = mock(UserService.class);
        var persistence = mock(SubscriptionPersistenceService.class);

        var mapper = new ObjectMapper();
        var service = new SubscriptionService(idemRepo, FIXED_CLOCK, gp, apple, userService, mapper, persistence);

        var user = new UserEntity();
        user.setId(1L);
        when(userService.getCurrentUser()).thenReturn(user);

        var expiry = NOW.plusSeconds(3600);
        var snap = new GooglePlayService.GoogleSnapshot(
                "fintrack_pro_month",
                "token123",
                NOW.minusSeconds(60),
                expiry,
                true,
                1,
                1,
                null,
                null
        );

        when(gp.verify(anyString(), anyString(), eq(true))).thenReturn(snap);

        var saved = new kz.finance.fintrack.model.SubscriptionEntity();
        saved.setProductId("fintrack_pro_month");
        saved.setExpiryDate(expiry);
        saved.setAutoRenewing(true);
        saved.setRevoked(false);

        when(persistence.persistGoogle(eq(user), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(saved);

        service.verifyGoogleAndSave(new GoogleVerifyRequest("token123", "fintrack_pro_month"), "idem-1");

        verify(idemRepo).save(argThat(e ->
                e.getUser() == user
                && e.getProvider() == SubscriptionProvider.GOOGLE
                && "idem-1".equals(e.getIdemKey())
                && e.getResponseJson() != null
        ));
    }

    @Test
    void verifyGoogle_whenCachedJsonBroken_shouldFallbackToVerify() {
        var idemRepo = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var apple = mock(AppleSk2Verifier.class);
        var userService = mock(UserService.class);
        var persistence = mock(SubscriptionPersistenceService.class);

        var mapper = new ObjectMapper();
        var service = new SubscriptionService(idemRepo, FIXED_CLOCK, gp, apple, userService, mapper, persistence);

        var user = new UserEntity();
        user.setId(1L);
        when(userService.getCurrentUser()).thenReturn(user);

        var idemEntity = new kz.finance.fintrack.model.IapIdempotencyEntity();
        idemEntity.setResponseJson("{broken-json");

        when(idemRepo.findByUserAndProviderAndIdemKey(eq(user), eq(SubscriptionProvider.GOOGLE), eq("k1")))
                .thenReturn(Optional.of(idemEntity));

        var expiry = NOW.plusSeconds(3600);
        when(gp.verify(anyString(), anyString(), eq(true))).thenReturn(
                new GooglePlayService.GoogleSnapshot("fintrack_pro_month", "t", NOW.minusSeconds(60), expiry, true, 1, 1, null, null)
        );

        var saved = new kz.finance.fintrack.model.SubscriptionEntity();
        saved.setProductId("fintrack_pro_month");
        saved.setExpiryDate(expiry);
        saved.setAutoRenewing(true);
        saved.setRevoked(false);

        when(persistence.persistGoogle(eq(user), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(saved);

        var res = service.verifyGoogleAndSave(new GoogleVerifyRequest("t", "fintrack_pro_month"), "k1");

        assertThat(res.status()).isEqualTo(EntitlementStatus.ENTITLED);
        verify(gp).verify(anyString(), anyString(), eq(true));
    }
}
