package kz.finance.fintrack.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.finance.fintrack.dto.subscription.GoogleVerifyRequest;
import kz.finance.fintrack.model.SubscriptionEntity;
import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.IapIdempotencyRepository;
import kz.finance.fintrack.service.UserService;
import kz.finance.fintrack.service.subscription.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceIdempotencyExtraTest {

    private static final Instant NOW = Instant.parse("2026-01-02T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void blankIdemKey_doesNotReadOrWriteIdemRepo() {
        var idem = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var persistence = mock(SubscriptionPersistenceService.class);

        var userService = mock(UserService.class);
        var u = new UserEntity();
        u.setId(1L);
        when(userService.getCurrentUser()).thenReturn(u);

        var service = new SubscriptionService(
                idem,
                FIXED_CLOCK,
                gp,
                mock(AppleSk2Verifier.class),
                mock(AppleReceiptVerifier.class),
                userService,
                new ObjectMapper(),
                persistence
        );

        when(gp.verify(anyString(), anyString(), eq(true)))
                .thenReturn(new GooglePlayService.GoogleSnapshot(
                        "fintrack_pro_month",
                        "t",
                        NOW,
                        NOW.plusSeconds(3600),
                        true,
                        1,
                        1,
                        null,
                        null
                ));

        var saved = new SubscriptionEntity();
        saved.setProductId("fintrack_pro_month");
        saved.setExpiryDate(NOW.plusSeconds(3600));
        saved.setAutoRenewing(true);
        saved.setRevoked(false);

        when(persistence.persistGoogle(eq(u), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(saved);

        service.verifyGoogleAndSave(new GoogleVerifyRequest("t", "fintrack_pro_month"), "   ");

        verify(idem, never()).findByUserAndProviderAndIdemKey(any(), any(), any());
        verify(idem, never()).save(any());
    }

    @Test
    void saveIdem_raceViolation_isSwallowed() {
        var idem = mock(IapIdempotencyRepository.class);
        var gp = mock(GooglePlayService.class);
        var persistence = mock(SubscriptionPersistenceService.class);

        var userService = mock(UserService.class);
        var u = new UserEntity();
        u.setId(1L);
        when(userService.getCurrentUser()).thenReturn(u);

        var service = new SubscriptionService(
                idem,
                FIXED_CLOCK,
                gp,
                mock(AppleSk2Verifier.class),
                mock(AppleReceiptVerifier.class),
                userService,
                new ObjectMapper(),
                persistence
        );

        when(gp.verify(anyString(), anyString(), eq(true)))
                .thenReturn(new GooglePlayService.GoogleSnapshot(
                        "fintrack_pro_month",
                        "t",
                        NOW,
                        NOW.plusSeconds(3600),
                        true,
                        1,
                        1,
                        null,
                        null
                ));

        var saved = new SubscriptionEntity();
        saved.setProductId("fintrack_pro_month");
        saved.setExpiryDate(NOW.plusSeconds(3600));
        saved.setAutoRenewing(true);
        saved.setRevoked(false);

        when(persistence.persistGoogle(eq(u), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(saved);

        doThrow(new DataIntegrityViolationException("dup"))
                .when(idem).save(any());

        assertThatCode(() ->
                service.verifyGoogleAndSave(
                        new GoogleVerifyRequest("t", "fintrack_pro_month"),
                        "idem-dup"
                )
        ).doesNotThrowAnyException();
    }
}
