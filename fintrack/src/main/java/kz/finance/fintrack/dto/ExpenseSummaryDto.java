package kz.finance.fintrack.dto;

import kz.finance.fintrack.dto.analytics.ChartDataDto;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseSummaryDto(
        BigDecimal totalAmount,
        BigDecimal average,
        String averageLabel,
        List<ChartDataDto> chartData,
        List<CategoryExpenseDto> categoryExpenses
) {
}
