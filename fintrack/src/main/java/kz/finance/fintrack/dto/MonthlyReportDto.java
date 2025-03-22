package kz.finance.fintrack.dto;

import java.math.BigDecimal;

public record MonthlyReportDto(int year, int month, BigDecimal totalExpenses) {
}
