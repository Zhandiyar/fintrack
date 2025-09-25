package kz.finance.fintrack.dto.subscription;

import jakarta.validation.constraints.NotBlank;

public record VerifyRequest(
       @NotBlank String purchaseToken,
       @NotBlank String productId,
       @NotBlank String packageName
) {}
