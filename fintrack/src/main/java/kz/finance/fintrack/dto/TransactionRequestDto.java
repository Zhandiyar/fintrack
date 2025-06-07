package kz.finance.fintrack.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import kz.finance.fintrack.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionRequestDto(
    Long id,
    
    @NotNull(message = "Сумма обязательна")
    @Positive(message = "Сумма должна быть положительной")
    BigDecimal amount,
    
    @NotNull(message = "Дата обязательна")
    LocalDateTime date,
    
    String comment,
    
    @NotNull(message = "Тип транзакции обязателен")
    TransactionType type,
    
    @NotNull(message = "Категория обязательна")
    Long categoryId
) {} 