package kz.finance.fintrack.controller;

import jakarta.validation.Valid;
import kz.finance.fintrack.dto.ApiResponse;
import kz.finance.fintrack.dto.subscription.AppleNotificationRequest;
import kz.finance.fintrack.dto.subscription.AppleVerifyRequest;
import kz.finance.fintrack.dto.subscription.EntitlementResponse;
import kz.finance.fintrack.dto.subscription.VerifyRequest;
import kz.finance.fintrack.service.subscription.AppleServerNotificationService;
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
    private final GoogleWebhookParser googleWebhookParser;
    private final AppleServerNotificationService appleServerNotificationService;


    /**
     * Верификация покупки (Android)
     * POST /api/subscription/google/verify
     */
    @PostMapping("/google/verify")
    public ResponseEntity<EntitlementResponse> verify(
            @Valid @RequestBody VerifyRequest req,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey
    ) {
        return ResponseEntity.ok(subscriptionService.verifyGoogleAndSave(req, idemKey));
    }

    /**
     * Верификация покупки (IOS)
     * POST /api/subscription/apple/verify
     */
    @PostMapping("/apple/verify")
    public ResponseEntity<EntitlementResponse> verifyApple(
            @Valid @RequestBody AppleVerifyRequest req,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey
    ) {
        return ResponseEntity.ok(subscriptionService.verifyAppleAndSave(req, idemKey));
    }

    /**
     * Получить текущий статус подписки пользователя
     * GET /api/subscription/entitlements/me
     */
    @GetMapping("/entitlements/me")
    public ResponseEntity<EntitlementResponse> me() {
        return ResponseEntity.ok(subscriptionService.myEntitlement());
    }

    /**
     * Webhook от Google Play (RTDN / PubSub push)
     * POST /api/subscription/google/rtnd
     */
    @PostMapping("/google/rtnd")
    public ResponseEntity<Void> rtnd(@RequestBody Map<String, Object> body) {
        GoogleWebhookParser.DeveloperNotification n = googleWebhookParser.parsePubSub(body);
        subscriptionService.applyGoogleRtnd(n);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/apple/notifications")
    public ResponseEntity<ApiResponse> notifications(@Valid @RequestBody AppleNotificationRequest req) {
        appleServerNotificationService.handleSignedPayload(req.signedPayload());
        return ResponseEntity.ok().build();
    }
}
