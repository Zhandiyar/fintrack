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

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class EmailService {

    @Value("${mailersend.api.key}")
    private String mailerSendApiKey;

    private static final String API_URL = "https://api.mailersend.com/v1/email";
    private static final String FROM_EMAIL = "support@fin-track.pro";
    private static final String FROM_NAME = "FinTrack";

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public void sendSimpleMessage(String to, String subject, String text) {
        try {
            Map<String, Object> body = Map.of(
                    "from", Map.of("email", FROM_EMAIL, "name", FROM_NAME),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "text", text
            );

            RequestBody requestBody = RequestBody.create(
                    mapper.writeValueAsString(body),
                    MediaType.get("application/json")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + mailerSendApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String error = response.body() != null ? response.body().string() : "No response body";
                    log.error("MailerSend error: {}", error);
                    throw new RuntimeException("Ошибка при отправке письма");
                }
                log.info("Письмо успешно отправлено на {}", to);
            }

        } catch (Exception e) {
            log.error("Ошибка при отправке письма через MailerSend", e);
            throw new RuntimeException("Ошибка при отправке письма", e);
        }
    }
}

