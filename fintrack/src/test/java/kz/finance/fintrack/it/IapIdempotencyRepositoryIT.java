package kz.finance.fintrack.it;

import kz.finance.fintrack.model.IapIdempotencyEntity;
import kz.finance.fintrack.model.SubscriptionProvider;
import kz.finance.fintrack.model.UserEntity;
import kz.finance.fintrack.repository.IapIdempotencyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IapIdempotencyRepositoryIT extends AbstractPostgresIT {

    @Autowired IapIdempotencyRepository repo;
    @Autowired EntityManager em;

    @Test
    void unique_user_provider_idemKey_enforced() {
        insertUserRow(1L);

        var userRef = userRef(1L);

        var e1 = new IapIdempotencyEntity();
        e1.setUser(userRef);
        e1.setProvider(SubscriptionProvider.GOOGLE);
        e1.setIdemKey("k1");
        e1.setResponseJson("{\"ok\":true}");
        e1.setCreatedAt(fixedNow());

        repo.saveAndFlush(e1);

        var e2 = new IapIdempotencyEntity();
        e2.setUser(userRef);
        e2.setProvider(SubscriptionProvider.GOOGLE);
        e2.setIdemKey("k1");
        e2.setResponseJson("{\"ok\":true}");
        e2.setCreatedAt(fixedNow());

        assertThatThrownBy(() -> repo.saveAndFlush(e2))
                .isInstanceOfAny(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void deleteOlderThan_works() {
        insertUserRow(1L);
        var userRef = userRef(1L);

        var old = new IapIdempotencyEntity();
        old.setUser(userRef);
        old.setProvider(SubscriptionProvider.GOOGLE);
        old.setIdemKey("old");
        old.setResponseJson("{}");
        old.setCreatedAt(fixedNow().minusSeconds(10_000));
        repo.saveAndFlush(old);

        var fresh = new IapIdempotencyEntity();
        fresh.setUser(userRef);
        fresh.setProvider(SubscriptionProvider.GOOGLE);
        fresh.setIdemKey("fresh");
        fresh.setResponseJson("{}");
        fresh.setCreatedAt(fixedNow());
        repo.saveAndFlush(fresh);

        int deleted = repo.deleteOlderThan(fixedNow().minusSeconds(1000));
        assertThat(deleted).isEqualTo(1);

        assertThat(repo.findByUserAndProviderAndIdemKey(userRef, SubscriptionProvider.GOOGLE, "old")).isEmpty();
        assertThat(repo.findByUserAndProviderAndIdemKey(userRef, SubscriptionProvider.GOOGLE, "fresh")).isPresent();
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
