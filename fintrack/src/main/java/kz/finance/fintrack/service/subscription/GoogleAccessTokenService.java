package kz.finance.fintrack.service.subscription;

import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class    GoogleAccessTokenService {

    @Value("${google.service-account-path}")
    private String serviceAccountPath;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiry;

    public synchronized String getAccessToken() {
        try {
            if (cachedAccessToken != null
                && tokenExpiry != null
                && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
                return cachedAccessToken;
            }

            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new FileInputStream(serviceAccountPath))
                    .createScoped(List.of("https://www.googleapis.com/auth/androidpublisher"));

            credentials.refreshIfExpired();
            var at = credentials.getAccessToken();
            if (at == null) {
                at = credentials.refreshAccessToken();
            }

            cachedAccessToken = at.getTokenValue();
            tokenExpiry = at.getExpirationTime().toInstant();
            return cachedAccessToken;
        } catch (Exception e) {
            log.error("Failed to fetch Google access token", e);
            throw new IllegalStateException("Google access token fetch failed", e);
        }
    }
}
