package kz.finance.fintrack.config;

import com.apple.itunes.storekit.model.Environment;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "apple")
public record AppleIapProperties(
        // App identifiers
        String bundleId,
        long appAppleId,

        // App Store Server API (SK2) credentials
        String issuerId,
        String keyId,
        Resource privateKeyP8,

        // Legacy verifyReceipt shared secret (StoreKit 1 receipts)
        String sharedSecret,

        // csv: "sku1,sku2"
        String allowedProducts,

        // PRODUCTION | SANDBOX (for preferred first try)
        Environment preferredEnvironment,

        // feature flags
        boolean enableOnlineChecks,

        // optional certs
        List<Resource> rootCerts,

        // verifyReceipt tuning
        VerifyReceiptProperties verifyReceipt
) {
    // безопасный дефолт (на случай отсутствия пропертей)
    public static final Set<String> DEFAULT_ALLOWED_PRODUCTS =
            Set.of("fintrack_pro_month", "fintrack_pro_year");

    public AppleIapProperties {
        if (verifyReceipt == null) {
            verifyReceipt = new VerifyReceiptProperties(3, 200, 512 * 1024);
        }
        if (preferredEnvironment == null) {
            preferredEnvironment = Environment.PRODUCTION;
        }
    }

    /**
     * Parsed allow-list from CSV. Can be empty if property is blank.
     */
    public Set<String> parsedAllowedProductSet() {
        if (!StringUtils.hasText(allowedProducts)) return Set.of();

        var set = new LinkedHashSet<String>();
        Arrays.stream(allowedProducts.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(set::add);

        return Set.copyOf(set);
    }

    /**
     * Effective allow-list: if config is empty -> safe defaults.
     */
    public Set<String> effectiveAllowedProductSet() {
        Set<String> parsed = parsedAllowedProductSet();
        return parsed.isEmpty() ? DEFAULT_ALLOWED_PRODUCTS : parsed;
    }

    public record VerifyReceiptProperties(
            int maxAttempts,
            long initialBackoffMs,
            int maxReceiptSizeChars
    ) {
        public VerifyReceiptProperties {
            if (maxAttempts <= 0) maxAttempts = 3;
            if (initialBackoffMs < 0) initialBackoffMs = 0;
            if (maxReceiptSizeChars <= 0) maxReceiptSizeChars = 512 * 1024;
        }
    }
}
