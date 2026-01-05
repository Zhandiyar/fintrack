package kz.finance.fintrack.repository;

import kz.finance.fintrack.model.IapIdempotencyEntity;
import kz.finance.fintrack.model.SubscriptionProvider;
import kz.finance.fintrack.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface IapIdempotencyRepository extends JpaRepository<IapIdempotencyEntity, Long> {
    Optional<IapIdempotencyEntity> findByUserAndProviderAndIdemKey(
            UserEntity user, SubscriptionProvider provider, String idemKey
    );
    @Modifying
    @Query("delete from IapIdempotencyEntity e where e.createdAt < :threshold")
    int deleteOlderThan(Instant threshold);
}
