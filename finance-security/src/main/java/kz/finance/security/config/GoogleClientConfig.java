package kz.finance.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google.client")
@Getter
@Setter
public class GoogleClientConfig {
    private String webClientId;
    private String androidClientId;
    private String iosClientId;
}
