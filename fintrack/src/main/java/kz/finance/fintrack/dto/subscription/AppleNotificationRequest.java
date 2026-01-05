package kz.finance.fintrack.dto.subscription;

import jakarta.validation.constraints.NotBlank;

public record AppleNotificationRequest(
        @NotBlank String signedPayload
) {}
