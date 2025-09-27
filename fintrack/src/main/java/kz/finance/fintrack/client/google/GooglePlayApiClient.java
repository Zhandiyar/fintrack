package kz.finance.fintrack.client.google;

import kz.finance.fintrack.config.GoogleFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(
        name = "googlePlayApiClient",
        url = "${google.api-url:https://androidpublisher.googleapis.com/androidpublisher/v3}",
        configuration = GoogleFeignConfig.class
)
public interface GooglePlayApiClient {

    // Verify
    @GetMapping("/applications/{packageName}/purchases/subscriptions/{productId}/tokens/{purchaseToken}")
    Map<String, Object> verifyPurchase(
            @PathVariable String packageName,
            @PathVariable String productId,
            @PathVariable String purchaseToken
    );

    // Acknowledge (обязательно для подписок, иначе рефанд через 3 дня)
    @PostMapping("/applications/{packageName}/purchases/subscriptions/{productId}/tokens/{purchaseToken}:acknowledge")
    void acknowledge(
            @PathVariable String packageName,
            @PathVariable String productId,
            @PathVariable String purchaseToken,
            @RequestBody Map<String, String> body // {"developerPayload":"..."} опц.
    );
}

