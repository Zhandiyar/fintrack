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
    TransactionCategoryDto category
    ) {
}