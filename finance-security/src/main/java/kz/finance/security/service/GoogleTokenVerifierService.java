package kz.finance.security.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.api.client.json.jackson2.JacksonFactory;

import java.util.Collections;

@Service
public class GoogleTokenVerifierService {

    @Value("${google.client.id}")
    private String CLIENT_ID;
    public GoogleIdToken.Payload verify(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new JacksonFactory())
                    .setAudience(Collections.singletonList(CLIENT_ID))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                return idToken.getPayload(); // содержит email, имя и т.д.
            } else {
                throw new RuntimeException("Invalid Google ID Token");
            }
        } catch (Exception e) {
            throw new RuntimeException("Google token verification failed", e);
        }
    }
}