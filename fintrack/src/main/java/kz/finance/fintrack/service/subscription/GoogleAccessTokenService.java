package kz.finance.fintrack.service.subscription;

import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class GoogleAccessTokenService {

    @Value("${google.service-account-path}")
    private String serviceAccountPath;

    private GoogleCredentials credentials;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiry;

    @PostConstruct
    void init() {
        try (var in = new FileInputStream(serviceAccountPath)) {
            this.credentials = GoogleCredentials.fromStream(in)
                    .createScoped(List.of("https://www.googleapis.com/auth/androidpublisher"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init GoogleCredentials", e);
        }
    }

    public void invalidate() {
        cachedAccessToken = null;
        tokenExpiry = null;
    }

    public String getAccessToken() {
        var exp = tokenExpiry;
        var token = cachedAccessToken;
        if (token != null && exp != null && Instant.now().isBefore(exp.minusSeconds(60))) return token;

        synchronized (this) {
            exp = tokenExpiry;
            token = cachedAccessToken;
            if (token != null && exp != null && Instant.now().isBefore(exp.minusSeconds(60))) return token;

            try {
                credentials.refreshIfExpired();
                var at = credentials.getAccessToken();
                if (at == null) at = credentials.refreshAccessToken();

                cachedAccessToken = at.getTokenValue();
                tokenExpiry = at.getExpirationTime().toInstant();
                return cachedAccessToken;

            } catch (Exception e) {
                log.error("Failed to fetch Google access token", e);
                throw new IllegalStateException("Google access token fetch failed", e);
            }
        }
    }
}
