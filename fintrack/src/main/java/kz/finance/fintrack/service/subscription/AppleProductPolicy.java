package kz.finance.fintrack.service.subscription;

import kz.finance.fintrack.config.AppleIapProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Set;

/**
 * Centralized Apple product allow-list policy.
 * Single source of truth: AppleIapProperties (apple.allowed-products).
 */
@Slf4j
@Component
public class AppleProductPolicy {

    @Getter
    private final Set<String> allowedProducts;

    public AppleProductPolicy(AppleIapProperties props) {
        Objects.requireNonNull(props, "AppleIapProperties must not be null");

        Set<String> parsed = props.parsedAllowedProductSet();
        this.allowedProducts = props.effectiveAllowedProductSet();

        if (parsed.isEmpty()) {
            log.warn("apple.allowed-products is empty. Falling back to defaults: {}", this.allowedProducts);
        } else {
            log.info("Apple allowed-products loaded: {}", this.allowedProducts);
        }
    }

    public boolean isAllowed(String productId) {
        String id = normalize(productId);
        return id != null && allowedProducts.contains(id);
    }

    public void requireAllowed(String productId) {
        String id = normalize(productId);
        if (id == null) throw new IllegalArgumentException("Apple productId is blank");
        if (!allowedProducts.contains(id)) throw new IllegalArgumentException("Unknown productId: " + id);
    }

    private static String normalize(String s) {
        if (!StringUtils.hasText(s)) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
