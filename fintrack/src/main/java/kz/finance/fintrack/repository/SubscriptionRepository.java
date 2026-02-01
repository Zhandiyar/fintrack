package kz.finance.fintrack.repository;

import kz.finance.fintrack.model.SubscriptionEntity;
import kz.finance.fintrack.model.SubscriptionProvider;
import kz.finance.fintrack.model.SubscriptionStatus;
import kz.finance.fintrack.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {

    Optional<SubscriptionEntity> findByProviderAndPurchaseToken(SubscriptionProvider provider, String purchaseToken);

    Optional<SubscriptionEntity> findByProviderAndOriginalTransactionId(SubscriptionProvider provider, String originalTransactionId);

    Optional<SubscriptionEntity> findByProviderAndAppleTransactionId(SubscriptionProvider provider, String appleTransactionId);

    List<SubscriptionEntity> findTop20ByUserOrderByExpiryDateDesc(UserEntity user);
        
    /**
     * Деактивирует все остальные активные подписки пользователя
     * того же провайдера, кроме текущей (по purchaseToken).
     *
     * Используется и для Apple, и для Google.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SubscriptionEntity s
           set s.active = false,
               s.status = :status,
               s.lastVerifiedAt = :now
         where s.user = :user
           and s.provider = :provider
           and s.active = true
           and s.purchaseToken <> :keepPurchaseToken
    """)
    int deactivateOthers(
            UserEntity user,
            SubscriptionProvider provider,
            String keepPurchaseToken,
            SubscriptionStatus status,
            Instant now
    );
}
