package kz.finance.fintrack.dto.subscription;

import kz.finance.fintrack.model.SubscriptionState;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class GoogleSubscriptionInfo {
    private LocalDateTime purchaseDate;
    private LocalDateTime expiryDate;
    private boolean active;
    private SubscriptionState status;
    private Integer cancelReason;

    public GoogleSubscriptionInfo(LocalDateTime purchaseDate, LocalDateTime expiryDate, boolean active, SubscriptionState status, Integer cancelReason) {
        this.purchaseDate = purchaseDate;
        this.expiryDate = expiryDate;
        this.active = active;
        this.status = status;
        this.cancelReason = cancelReason;
    }
}
