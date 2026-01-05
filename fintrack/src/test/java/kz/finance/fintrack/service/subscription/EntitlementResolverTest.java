package kz.finance.fintrack.service.subscription;

import kz.finance.fintrack.dto.subscription.EntitlementStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EntitlementResolverTest {

    @Test
    void revoked_alwaysRevoked() {
        var now = Instant.now();
        var res = EntitlementResolver.resolve(true, now.plusSeconds(10), null, now);
        assertThat(res).isEqualTo(EntitlementStatus.REVOKED);
    }

    @Test
    void expiryNull_isNone() {
        var now = Instant.now();
        var res = EntitlementResolver.resolve(false, null, null, now);
        assertThat(res).isEqualTo(EntitlementStatus.NONE);
    }

    @Test
    void graceUntilFuture_isInGrace() {
        var now = Instant.now();
        var res = EntitlementResolver.resolve(false, now.minusSeconds(10), now.plusSeconds(60), now);
        assertThat(res).isEqualTo(EntitlementStatus.IN_GRACE);
    }

    @Test
    void expiryFuture_isEntitled() {
        var now = Instant.now();
        var res = EntitlementResolver.resolve(false, now.plusSeconds(60), null, now);
        assertThat(res).isEqualTo(EntitlementStatus.ENTITLED);
    }

    @Test
    void expiryPast_isExpired() {
        var now = Instant.now();
        var res = EntitlementResolver.resolve(false, now.minusSeconds(1), null, now);
        assertThat(res).isEqualTo(EntitlementStatus.EXPIRED);
    }
}
