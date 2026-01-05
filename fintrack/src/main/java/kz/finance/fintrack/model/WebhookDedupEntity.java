package kz.finance.fintrack.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "iap_webhook_dedup")
public class WebhookDedupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "iap_webhook_dedup_seq")
    @SequenceGenerator(name = "iap_webhook_dedup_seq", sequenceName = "seq_iap_webhook_dedup_id", allocationSize = 50)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private SubscriptionProvider provider;

    @Column(name = "event_id", nullable = false, length = 96)
    private String eventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
