package kz.finance.fintrack.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsSummaryDto(
        BigDecimal currentIncome,
        BigDecimal currentExpense,
        BigDecimal netIncome,
        Double incomeChange,
        Double expenseChange,
        List<ChartPointDto> chartData
) {
}