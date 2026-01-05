package kz.finance.fintrack.repository;

import kz.finance.fintrack.model.WebhookDedupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface WebhookDedupRepository extends JpaRepository<WebhookDedupEntity, Long> {

    @Modifying
    @Query(value = """
        insert into iap_webhook_dedup(provider, event_id, created_at)
        values (:provider, :eventId, now())
        on conflict do nothing
        """, nativeQuery = true)
    int insertIgnore(String provider, String eventId);

    @Modifying
    @Query("delete from WebhookDedupEntity e where e.createdAt < :threshold")
    int deleteOlderThan(Instant threshold);
}
