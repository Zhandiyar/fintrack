package kz.finance.fintrack.dto.subscription;

import jakarta.validation.constraints.NotBlank;

public record GoogleVerifyRequest(
       @NotBlank String purchaseToken,
       @NotBlank String productId
) {}
