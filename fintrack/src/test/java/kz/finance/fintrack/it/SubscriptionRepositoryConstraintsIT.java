package kz.finance.fintrack.it;

import jakarta.persistence.EntityManager;
import kz.finance.fintrack.model.*;
import kz.finance.fintrack.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SubscriptionRepositoryConstraintsIT extends AbstractPostgresIT {

    @Autowired SubscriptionRepository repo;
    @Autowired EntityManager em;

    @Test
    void unique_provider_purchaseToken_enforced() {
        insertUserRow(1L);
        var user = userRef(1L);
        var now = fixedNow();

        repo.saveAndFlush(buildSub(user, SubscriptionProvider.GOOGLE, "token-uniq", null, null, now));
        assertThatThrownBy(() ->
                repo.saveAndFlush(buildSub(user, SubscriptionProvider.GOOGLE, "token-uniq", null, null, now))
        ).isInstanceOfAny(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void unique_provider_originalTransactionId_enforced_whenNotNull() {
        insertUserRow(1L);
        var user = userRef(1L);
        var now = fixedNow();

        repo.saveAndFlush(buildSub(user, SubscriptionProvider.APPLE, "pt-1", "orig-uniq", "tx-1", now));
        assertThatThrownBy(() ->
                repo.saveAndFlush(buildSub(user, SubscriptionProvider.APPLE, "pt-2", "orig-uniq", "tx-2", now))
        ).isInstanceOfAny(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private SubscriptionEntity buildSub(
            UserEntity user,
            SubscriptionProvider provider,
            String purchaseToken,
            String origTx,
            String appleTx,
            Instant now
    ) {
        var s = new SubscriptionEntity();
        s.setUser(user);
        s.setProvider(provider);
        s.setProductId("sku");
        s.setPurchaseToken(purchaseToken);

        s.setOriginalTransactionId(origTx);
        s.setAppleTransactionId(appleTx);

        s.setPurchaseDate(now.minusSeconds(100));
        s.setExpiryDate(now.plusSeconds(1000));
        s.setGraceUntil(null);

        s.setRevoked(false);
        s.setStatus(SubscriptionStatus.ENTITLED);
        s.setActive(true);

        s.setAutoRenewing(true);
        s.setAcknowledgementState(1);
        s.setCancelReason(null);
        s.setPurchaseState(SubscriptionState.ACTIVE);

        s.setLastVerifiedAt(now);
        s.setEnvironment(StoreEnvironment.SANDBOX);
        return s;
    }

    private void insertUserRow(Long id) {
        em.createNativeQuery("insert into users(id) values (:id) on conflict do nothing")
                .setParameter("id", id)
                .executeUpdate();
    }

    private UserEntity userRef(Long id) {
        var u = new UserEntity();
        u.setId(id);
        return u;
    }
}
