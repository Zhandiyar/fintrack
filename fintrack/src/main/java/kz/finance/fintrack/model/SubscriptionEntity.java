package kz.finance.fintrack.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "subscriptions")
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "subscription_seq")
    @SequenceGenerator(name = "subscription_seq", sequenceName = "seq_subscriptions_id", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private SubscriptionProvider provider = SubscriptionProvider.GOOGLE;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "purchase_token", nullable = false)
    private String purchaseToken;

    @Column(name = "purchase_date", nullable = false)
    private Instant purchaseDate;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Column(name = "grace_until")
    private Instant graceUntil;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_state", nullable = false)
    private SubscriptionState purchaseState;

    @Column(name = "cancel_reason")
    private Integer cancelReason;

    @Column(name = "auto_renewing", nullable = false)
    private boolean autoRenewing = true;

    @Column(name = "acknowledgement_state", nullable = false)
    private int acknowledgementState = 0;

    @Column(name = "original_transaction_id")
    private String originalTransactionId;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    // ===== Apple-specific =====

    @Column(name = "apple_transaction_id", length = 64)
    private String appleTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", length = 16)
    private StoreEnvironment environment;

    @Column(name = "revocation_date")
    private Instant revocationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status = SubscriptionStatus.NONE;

    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = SubscriptionStatus.NONE;
        if (purchaseState == null) purchaseState = SubscriptionState.UNKNOWN;
    }
}
