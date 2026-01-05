package kz.finance.fintrack.it;

import kz.finance.fintrack.exception.FinTrackException;
import kz.finance.fintrack.model.*;
import kz.finance.fintrack.repository.SubscriptionRepository;
import kz.finance.fintrack.service.subscription.SubscriptionPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SubscriptionPersistenceService.class)
class SubscriptionPersistenceServiceIT extends AbstractPostgresIT {

    @Autowired
    SubscriptionPersistenceService service;
    @Autowired
    SubscriptionRepository repo;
    @Autowired
    EntityManager em;

    @Test
    void persistApple_upsertByTxId_whenOrigTxNull() {
        insertUserRow(1L);
        var user = userRef(1L);
        var now = fixedNow();

        var s1 = service.persistApple(
                user,
                "fintrack_pro_month",
                "tx-100",
                null,
                now.minusSeconds(100),
                now.plusSeconds(1000),
                null,
                true,
                com.apple.itunes.storekit.model.Environment.SANDBOX,
                false,
                null,
                now
        );

        var s2 = service.persistApple(
                user,
                "fintrack_pro_year",
                "tx-100",
                null,
                now.minusSeconds(50),
                now.plusSeconds(5000),
                null,
                true,
                com.apple.itunes.storekit.model.Environment.SANDBOX,
                false,
                null,
                now
        );

        assertThat(s2.getId()).isEqualTo(s1.getId());
        assertThat(s2.getProductId()).isEqualTo("fintrack_pro_year");
        assertThat(s2.getExpiryDate()).isEqualTo(now.plusSeconds(5000));
    }

    @Test
    void persistApple_environmentNull_defaultsToProduction() {
        insertUserRow(1L);
        var user = userRef(1L);
        var now = fixedNow();

        var s = service.persistApple(
                user,
                "fintrack_pro_month",
                "tx-env",
                "orig-env",
                now.minusSeconds(100),
                now.plusSeconds(1000),
                null,
                true,
                null, // env=null
                false,
                null,
                now
        );

        assertThat(s.getEnvironment()).isEqualTo(StoreEnvironment.PRODUCTION);
    }

    @Test
    void persistApple_expiresNull_andNotRevoked_resultsNoneInactive() {
        insertUserRow(1L);
        var user = userRef(1L);
        var now = fixedNow();

        var s = service.persistApple(
                user,
                "fintrack_pro_month",
                "tx-null-exp",
                "orig-null-exp",
                now.minusSeconds(100),
                null, // expiresAt=null
                null,
                true,
                com.apple.itunes.storekit.model.Environment.SANDBOX,
                false,
                null,
                now
        );

        assertThat(s.isRevoked()).isFalse();
        assertThat(s.isActive()).isFalse();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.NONE);
    }

    @Test
    void persistApple_ownershipProtection_throws403() {
        insertUserRow(1L);
        insertUserRow(2L);
        var u1 = userRef(1L);
        var u2 = userRef(2L);
        var now = fixedNow();

        service.persistApple(
                u1,
                "fintrack_pro_month",
                "tx-own",
                "orig-own",
                now.minusSeconds(100),
                now.plusSeconds(1000),
                null,
                true,
                com.apple.itunes.storekit.model.Environment.SANDBOX,
                false,
                null,
                now
        );

        assertThatThrownBy(() -> service.persistApple(
                u2,
                "fintrack_pro_month",
                "tx-own",
                "orig-own",
                now.minusSeconds(100),
                now.plusSeconds(1000),
                null,
                true,
                com.apple.itunes.storekit.model.Environment.SANDBOX,
                false,
                null,
                now
        )).isInstanceOf(FinTrackException.class)
                .satisfies(ex -> assertThat(((FinTrackException) ex).getStatus()).isEqualTo(403));
    }


    @Test
    void persistGoogle_createsAndUpdatesSameToken() {
        insertUserRow(1L);
        var user = userRef(1L);

        Instant now = fixedNow();
        Instant expiry1 = now.plusSeconds(3600);

        var s1 = service.persistGoogle(
                user,
                "fintrack_pro_month",
                "token-1",
                now.minusSeconds(60),
                expiry1,
                null,
                1,
                null,
                true,
                1,
                now
        );

        assertThat(s1.getId()).isNotNull();
        assertThat(s1.getProvider()).isEqualTo(SubscriptionProvider.GOOGLE);
        assertThat(s1.getPurchaseToken()).isEqualTo("token-1");
        assertThat(s1.getExpiryDate()).isEqualTo(expiry1);
        assertThat(s1.isActive()).isTrue();
        assertThat(s1.getStatus()).isEqualTo(SubscriptionStatus.ENTITLED);

        Instant expiry2 = now.plusSeconds(7200);
        var s2 = service.persistGoogle(
                user,
                "fintrack_pro_month",
                "token-1",
                now.minusSeconds(120),
                expiry2,
                null,
                1,
                null,
                true,
                1,
                now
        );

        assertThat(s2.getId()).isEqualTo(s1.getId());
        assertThat(s2.getExpiryDate()).isEqualTo(expiry2);
    }

    @Test
    void persistGoogle_ownershipProtection_throws403() {
        insertUserRow(1L);
        insertUserRow(2L);

        var u1 = userRef(1L);
        var u2 = userRef(2L);

        Instant now = fixedNow();

        service.persistGoogle(
                u1, "fintrack_pro_month", "token-x",
                now.minusSeconds(10), now.plusSeconds(1000),
                null, 1, null, true, 1, now
        );

        assertThatThrownBy(() -> service.persistGoogle(
                u2, "fintrack_pro_month", "token-x",
                now.minusSeconds(10), now.plusSeconds(1000),
                null, 1, null, true, 1, now
        ))
                .isInstanceOf(FinTrackException.class)
                .satisfies(ex -> assertThat(((FinTrackException) ex).getStatus()).isEqualTo(403));
    }

    @Test
    void persistGoogle_inGrace_whenGraceFutureEvenIfExpired() {
        insertUserRow(1L);
        var user = userRef(1L);

        Instant now = fixedNow();
        Instant expiryPast = now.minusSeconds(10);
        Instant graceFuture = now.plusSeconds(600);

        var s = service.persistGoogle(
                user,
                "fintrack_pro_month",
                "token-grace",
                now.minusSeconds(100),
                expiryPast,
                graceFuture,
                1,
                null,
                true,
                1,
                now
        );

        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.IN_GRACE);
        assertThat(s.isActive()).isTrue();
    }

    @Test
    void persistApple_upsertByOriginalTx_andRevokedSticky() {
        insertUserRow(1L);
        var user = userRef(1L);

        Instant now = fixedNow();

        var s1 = service.persistApple(
                user,
                "fintrack_pro_month",
                "tx-1",
                "orig-1",
                now.minusSeconds(100),
                now.plusSeconds(1000),
                null,
                true,
                com.apple.itunes.storekit.model.Environment.SANDBOX,
                false,
                null,
                now
        );

        assertThat(s1.getProvider()).isEqualTo(SubscriptionProvider.APPLE);
        assertThat(s1.getPurchaseToken()).isEqualTo("orig-1"); // stable key
        assertThat(s1.getOriginalTransactionId()).isEqualTo("orig-1");
        assertThat(s1.getAppleTransactionId()).isEqualTo("tx-1");
        assertThat(s1.getEnvironment()).isEqualTo(StoreEnvironment.SANDBOX);
        assertThat(s1.isRevoked()).isFalse();
        assertThat(s1.getStatus()).isEqualTo(SubscriptionStatus.ENTITLED);

        // теперь приходит ревокация
        var s2 = service.persistApple(
                user,
                "fintrack_pro_month",
                "tx-2",
                "orig-1",
                now.minusSeconds(100),
                now.plusSeconds(1000),
                null,
                false,
                com.apple.itunes.storekit.model.Environment.SANDBOX,
                true,
                now.minusSeconds(5),
                now
        );

        assertThat(s2.getId()).isEqualTo(s1.getId()); // upsert по origTx
        assertThat(s2.isRevoked()).isTrue();
        assertThat(s2.getStatus()).isEqualTo(SubscriptionStatus.REVOKED);

        // попытка "разревокать" не должна снять revoked
        var s3 = service.persistApple(
                user,
                "fintrack_pro_month",
                "tx-3",
                "orig-1",
                now.minusSeconds(100),
                now.plusSeconds(2000),
                null,
                true,
                com.apple.itunes.storekit.model.Environment.SANDBOX,
                false,
                null,
                now
        );

        assertThat(s3.getId()).isEqualTo(s1.getId());
        assertThat(s3.isRevoked()).isTrue();   // revoked должен остаться true
        assertThat(s3.getStatus()).isEqualTo(SubscriptionStatus.REVOKED); // и статус тоже
    }

    @Test
    void findBestForUser_prefersEntitledOverExpired() {
        insertUserRow(1L);
        var user = userRef(1L);

        Instant now = fixedNow();

        // expired
        var a = new SubscriptionEntity();
        a.setUser(user);
        a.setProvider(SubscriptionProvider.GOOGLE);
        a.setProductId("fintrack_pro_month");
        a.setPurchaseToken("t-exp");
        a.setPurchaseDate(now.minusSeconds(10_000));
        a.setExpiryDate(now.minusSeconds(10));
        a.setGraceUntil(null);
        a.setRevoked(false);
        a.setAutoRenewing(false);
        a.setAcknowledgementState(1);
        a.setPurchaseState(SubscriptionState.EXPIRED);
        a.setActive(false);
        a.setStatus(SubscriptionStatus.EXPIRED);
        a.setLastVerifiedAt(now);
        repo.saveAndFlush(a);

        // entitled
        var b = new SubscriptionEntity();
        b.setUser(user);
        b.setProvider(SubscriptionProvider.GOOGLE);
        b.setProductId("fintrack_pro_year");
        b.setPurchaseToken("t-ok");
        b.setPurchaseDate(now.minusSeconds(1000));
        b.setExpiryDate(now.plusSeconds(10_000));
        b.setGraceUntil(null);
        b.setRevoked(false);
        b.setAutoRenewing(true);
        b.setAcknowledgementState(1);
        b.setPurchaseState(SubscriptionState.ACTIVE);
        b.setActive(true);
        b.setStatus(SubscriptionStatus.ENTITLED);
        b.setLastVerifiedAt(now);
        repo.saveAndFlush(b);

        var best = service.findBestForUser(user, now);
        assertThat(best).isNotNull();
        assertThat(best.getPurchaseToken()).isEqualTo("t-ok");
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
