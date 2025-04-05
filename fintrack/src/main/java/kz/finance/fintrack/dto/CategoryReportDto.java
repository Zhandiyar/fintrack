package kz.finance.fintrack.dto;

import kz.finance.fintrack.model.ExpenseCategory;

import java.math.BigDecimal;

public record CategoryReportDto(ExpenseCategory expenseCategory, BigDecimal bigDecimal) {
}
