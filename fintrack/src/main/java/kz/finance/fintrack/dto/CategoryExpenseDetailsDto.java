package kz.finance.fintrack.dto;

import kz.finance.fintrack.model.ExpenseCategory;

import java.math.BigDecimal;
import java.util.List;

public record CategoryExpenseDetailsDto(
        ExpenseCategory category,
        BigDecimal amount,
        BigDecimal percentage,
        List<ExpenseDto> expenses
) {
}
