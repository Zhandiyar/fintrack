package kz.finance.fintrack.service.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.finance.fintrack.exception.FinTrackException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoogleWebhookParser {

    private final ObjectMapper mapper;

    /** Официальный формат: { message: { data: base64(JSON) } } */
    public DeveloperNotification parsePubSub(Map<String, Object> body) {
        try {
            Object msgObj = body.get("message");
            if (!(msgObj instanceof Map<?, ?> msg)) {
                throw new FinTrackException(400, "RTDN payload missing message");
            }
            Object dataObj = msg.get("data");
            if (!(dataObj instanceof String b64) || b64.isBlank()) {
                throw new FinTrackException(400, "RTDN payload missing message.data");
            }
            byte[] json = Base64.getDecoder().decode(b64);
            return mapper.readValue(json, DeveloperNotification.class);
        } catch (FinTrackException e) {
            throw e;
        } catch (Exception e) {
            throw new FinTrackException(400, "RTDN parse error: invalid payload");
        }
    }


    // === Вспомогательные записи под RTDN ===
    public record DeveloperNotification(
            String version,
            String packageName,
            Long eventTimeMillis,
            SubscriptionNotification subscriptionNotification,
            OneTimeProductNotification oneTimeProductNotification,
            TestNotification testNotification
    ) {}

    public record SubscriptionNotification(
            Integer notificationType,
            String purchaseToken,
            String subscriptionId
    ) {}

    public record OneTimeProductNotification(
            Integer notificationType, String purchaseToken, String sku
    ) {}

    public record TestNotification(String version) {}
}

