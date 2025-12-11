package kz.finance.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class EmailService {

    @Value("${resend.api.key}")
    private String resendApiKey;
    private static final String API_URL = "https://api.resend.com/emails";
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public void sendSimpleMessage(String to, String subject, String text) {
        try {
            Map<String, String> body = Map.of(
                    "from", "FinTrack <support@fin-track.pro>",
                    "to", to,
                    "subject", subject,
                    "text", text
            );

            RequestBody requestBody = RequestBody.create(
                    mapper.writeValueAsString(body),
                    MediaType.get("application/json")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + resendApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "empty";
                    log.error("Resend failed with status {}: {}", response.code(), responseBody);
                    throw new RuntimeException("Email sending failed: " + responseBody);
                }

                log.info("Письмо успешно отправлено на {}", to);
            }

        } catch (Exception e) {
            log.error("Ошибка при отправке письма", e);
            throw new RuntimeException("Ошибка при отправке письма", e);
        }
    }
}

