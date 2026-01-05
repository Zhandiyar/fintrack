package kz.finance.fintrack.repository;

import kz.finance.fintrack.model.SubscriptionEntity;
import kz.finance.fintrack.model.SubscriptionProvider;
import kz.finance.fintrack.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {

    Optional<SubscriptionEntity> findByProviderAndPurchaseToken(SubscriptionProvider provider, String purchaseToken);

    Optional<SubscriptionEntity> findByProviderAndOriginalTransactionId(SubscriptionProvider provider, String originalTransactionId);

    Optional<SubscriptionEntity> findByProviderAndAppleTransactionId(SubscriptionProvider provider, String appleTransactionId);

    List<SubscriptionEntity> findTop20ByUserOrderByExpiryDateDesc(UserEntity user);
}
