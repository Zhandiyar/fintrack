package kz.finance.fintrack.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "iap_idempotency")
public class IapIdempotencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "iap_idempotency_seq")
    @SequenceGenerator(name = "iap_idempotency_seq", sequenceName = "seq_iap_idempotency_id", allocationSize = 50)
    private Long id;

    @Column(name = "idem_key", nullable = false, length = 128)
    private String idemKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private SubscriptionProvider provider;

    @Column(name = "response_json", nullable = false, columnDefinition = "text")
    private String responseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
