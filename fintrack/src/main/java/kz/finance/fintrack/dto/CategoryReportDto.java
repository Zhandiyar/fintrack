package kz.finance.fintrack.dto;

import java.math.BigDecimal;

public record CategoryReportDto(ExpenseCategory expenseCategory, BigDecimal bigDecimal) {
}
