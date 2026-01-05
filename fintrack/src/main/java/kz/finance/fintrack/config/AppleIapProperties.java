package kz.finance.fintrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.util.List;

@ConfigurationProperties(prefix = "apple")
public record AppleIapProperties(
        String bundleId,
        String issuerId,
        String keyId,
        Resource privateKeyP8,
        long appAppleId,
        String allowedProducts,    // csv
        String preferredEnvironment, // PRODUCTION | SANDBOX
        boolean enableOnlineChecks,
        List<Resource> rootCerts
) {}
