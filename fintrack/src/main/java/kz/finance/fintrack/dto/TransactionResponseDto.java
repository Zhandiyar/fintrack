package kz.finance.fintrack.dto;

import kz.finance.fintrack.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponseDto(
    Long id,
    BigDecimal amount,
    LocalDateTime date,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String comment,
    TransactionType type,
    Long categoryId,
    String categoryNameRu,
    String categoryNameEn
) {
    public String getCategoryName(String lang) {
        return "en".equalsIgnoreCase(lang) ? categoryNameEn : categoryNameRu;
    }
}