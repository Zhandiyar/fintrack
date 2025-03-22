package kz.finance.fintrack.dto;

import java.math.BigDecimal;

public record CategoryExpenseDto(
        ExpenseCategory category,
        BigDecimal amount,
        int percentage
) {
    public CategoryExpenseDto(ExpenseCategory category, BigDecimal amount) {
        this(category, amount, 0);
    }
}
