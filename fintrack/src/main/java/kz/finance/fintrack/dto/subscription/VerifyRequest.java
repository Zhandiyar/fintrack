package kz.finance.fintrack.dto.subscription;

public record VerifyRequest(
        String purchaseToken,
        String productId,
        String packageName
) {}
