package kz.finance.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apple")
public record AppleProperties(
        String issuer,
        String audience,
        String jwksUrl
) {
}
