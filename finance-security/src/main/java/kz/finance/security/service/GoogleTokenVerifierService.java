package kz.finance.security.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import kz.finance.security.exception.TokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class GoogleTokenVerifierService {

    public GoogleIdToken.Payload verify(String idTokenString, String expectedClientId) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    JacksonFactory.getDefaultInstance()
            )
                    .setAudience(List.of(expectedClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                throw new TokenException("Invalid Google ID token");
            }

            return idToken.getPayload();
        } catch (TokenException e) {
            throw e;
        } catch (Exception e) {
            throw new TokenException("Google token verification failed");
        }
    }
}