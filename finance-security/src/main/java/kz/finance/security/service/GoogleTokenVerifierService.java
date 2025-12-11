package kz.finance.security.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import kz.finance.security.config.GoogleClientConfig;
import kz.finance.security.exception.TokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleTokenVerifierService {

    private final GoogleClientConfig config;

    private static final List<String> VALID_ISSUERS = List.of(
            "accounts.google.com",
            "https://accounts.google.com"
    );

    public GoogleIdToken.Payload verify(String idTokenString) {
        try {
            List<String> allowedClientIds = List.of(
                    config.getAndroidClientId(),
                    config.getIosClientId(),
                    config.getWebClientId()
            );

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    JacksonFactory.getDefaultInstance()
            )
                    .setAudience(allowedClientIds)
                    .build();

            GoogleIdToken token = verifier.verify(idTokenString);

            if (token == null) {
                throw new TokenException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = token.getPayload();

            validateIssuer(payload);
            validateExpiration(payload);
            validateEmail(payload);

            return payload;

        } catch (TokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google token verification failed", e);
            throw new TokenException("Google token verification failed");
        }
    }

    private void validateIssuer(GoogleIdToken.Payload payload) {
        String issuer = payload.getIssuer();
        if (!VALID_ISSUERS.contains(issuer)) {
            throw new TokenException("Invalid issuer: " + issuer);
        }
    }

    private void validateExpiration(GoogleIdToken.Payload payload) {
        if (payload.getExpirationTimeSeconds() == null) {
            throw new TokenException("Token has no expiration");
        }
        long expMillis = payload.getExpirationTimeSeconds() * 1000;
        if (expMillis < System.currentTimeMillis()) {
            throw new TokenException("Google ID token is expired");
        }
    }

    private void validateEmail(GoogleIdToken.Payload payload) {
        Object verified = payload.get("email_verified");
        boolean isVerified = verified instanceof Boolean b ? b : Boolean.parseBoolean(verified.toString());
        if (!isVerified) {
            throw new TokenException("Google email is not verified");
        }
    }
}
