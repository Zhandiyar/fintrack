package kz.finance.fintrack.dto.subscription;

import jakarta.validation.constraints.NotBlank;

public record AppleVerifyRequest(
        @NotBlank String transactionId,
        @NotBlank String productId
) {}
