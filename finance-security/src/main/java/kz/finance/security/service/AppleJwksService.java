package kz.finance.security.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.finance.security.config.AppleProperties;
import kz.finance.security.exception.AppleAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppleJwksService {

    private final AppleProperties props;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Cacheable("applePublicKeys")
    public JsonNode getKeys() {
        try {
            Request request = new Request.Builder()
                    .url(props.jwksUrl())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new AppleAuthException("Failed to fetch Apple public keys: " + response.code());
                }

                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode keys = root.get("keys");

                if (keys == null || !keys.isArray()) {
                    throw new AppleAuthException("Apple JWKS response has no 'keys'");
                }

                return keys;
            }
        } catch (Exception e) {
            log.error("Error fetching Apple public keys", e);
            throw new AppleAuthException("Failed to fetch Apple public keys", e);
        }
    }
}
