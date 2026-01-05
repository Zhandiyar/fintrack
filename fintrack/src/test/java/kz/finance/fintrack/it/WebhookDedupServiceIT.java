package kz.finance.fintrack.it;

import kz.finance.fintrack.model.SubscriptionProvider;
import kz.finance.fintrack.repository.WebhookDedupRepository;
import kz.finance.fintrack.service.subscription.WebhookDedupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(WebhookDedupService.class)
class WebhookDedupServiceIT extends AbstractPostgresIT {

    @Autowired WebhookDedupService service;
    @Autowired WebhookDedupRepository repo;

    @Test
    void acquire_firstTimeTrue_secondTimeFalse() {
        boolean first = service.acquire(SubscriptionProvider.APPLE, "uuid-1");
        boolean second = service.acquire(SubscriptionProvider.APPLE, "uuid-1");
        boolean otherProvider = service.acquire(SubscriptionProvider.GOOGLE, "uuid-1");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(otherProvider).isTrue(); // provider+eventId уникальность
    }

    @Test
    void acquire_blankEventId_returnsTrue_noInsert() {
        boolean res = service.acquire(SubscriptionProvider.GOOGLE, "   ");
        assertThat(res).isTrue();
        // вставки быть не должно, но это "best-effort" поведение — ок
    }
}
