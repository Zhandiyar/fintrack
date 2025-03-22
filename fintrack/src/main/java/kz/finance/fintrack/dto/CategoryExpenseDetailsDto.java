package kz.finance.fintrack.dto;

import java.math.BigDecimal;
import java.util.List;

public record CategoryExpenseDetailsDto(
        ExpenseCategory category,
        BigDecimal amount,
        int percentage,
        List<ExpenseDto> expenses
) {
}
