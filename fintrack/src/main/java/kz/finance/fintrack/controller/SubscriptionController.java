package kz.finance.fintrack.controller;

import kz.finance.fintrack.dto.subscription.EntitlementResponse;
import kz.finance.fintrack.dto.subscription.VerifyRequest;
import kz.finance.fintrack.service.subscription.GoogleWebhookParser;
import kz.finance.fintrack.service.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Верификация покупки (Android)
     * POST /iap/google/verify
     */
    @PostMapping("/google/verify")
    public ResponseEntity<EntitlementResponse> verify(
            @RequestBody VerifyRequest req,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey
    ) {
        return ResponseEntity.ok(subscriptionService.verifyAndSave(req, idemKey));
    }

    /**
     * Получить текущий статус подписки пользователя
     * GET /iap/entitlements/me
     */
    @GetMapping("/entitlements/me")
    public ResponseEntity<EntitlementResponse> me() {
        return ResponseEntity.ok(subscriptionService.myEntitlement());
    }

    /**
     * Webhook от Google Play (RTDN / PubSub push)
     * POST /iap/google/rtnd
     */
    @PostMapping("/google/rtnd")
    public ResponseEntity<Void> rtnd(@RequestBody Map<String, Object> body) {
        GoogleWebhookParser.DeveloperNotification n = GoogleWebhookParser.parsePubSub(body);
        subscriptionService.applyRtnd(n);
        return ResponseEntity.ok().build();
    }
}
