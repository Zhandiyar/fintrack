package kz.finance.fintrack.service.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

@Component
public class GoogleWebhookParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Официальный формат: { message: { data: base64(JSON) } } */
    public static DeveloperNotification parsePubSub(Map<String, Object> body) {
        try {
            Map<?, ?> msg = (Map<?, ?>) body.get("message");
            String b64 = (String) msg.get("data");
            byte[] json = Base64.getDecoder().decode(b64);
            return MAPPER.readValue(json, DeveloperNotification.class);
        } catch (Exception e) {
            throw new RuntimeException("RTDN parse error", e);
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
            Integer notificationType,   // см. mapping ниже
            String purchaseToken,
            String subscriptionId
    ) {}

    public record OneTimeProductNotification(
            Integer notificationType, String purchaseToken, String sku
    ) {}

    public record TestNotification(String version) {}
}

