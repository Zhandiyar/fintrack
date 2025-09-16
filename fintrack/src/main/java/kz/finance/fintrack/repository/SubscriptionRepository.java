package kz.finance.fintrack.repository;

import kz.finance.fintrack.model.SubscriptionEntity;
import kz.finance.fintrack.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {
    Optional<SubscriptionEntity> findByPurchaseToken(String purchaseToken);
    List<SubscriptionEntity> findAllByUser(UserEntity user);
    Optional<SubscriptionEntity> findTopByUserOrderByExpiryDateDesc(UserEntity user);
}
