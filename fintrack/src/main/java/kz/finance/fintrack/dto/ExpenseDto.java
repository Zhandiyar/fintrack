package kz.finance.fintrack.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseDto(Long id, ExpenseCategory category, BigDecimal amount, String description, LocalDateTime date) {
}
