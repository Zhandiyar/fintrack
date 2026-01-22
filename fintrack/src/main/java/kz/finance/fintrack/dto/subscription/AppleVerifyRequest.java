package kz.finance.fintrack.dto.subscription;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record AppleVerifyRequest(
        @NotBlank String productId,
        String transactionId,
        String signedTransactionInfo,
        String appReceipt
) {
    @AssertTrue(message = "Provide transactionId, signedTransactionInfo or appReceipt")
    public boolean hasAnyProof() {
        return notBlank(transactionId) || notBlank(signedTransactionInfo) || notBlank(appReceipt);
    }


    public boolean hasTransactionId() {
        return notBlank(transactionId);
    }
    public boolean hasSignedTx()      { return notBlank(signedTransactionInfo); }

    public boolean hasReceipt() {
        return notBlank(appReceipt);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
