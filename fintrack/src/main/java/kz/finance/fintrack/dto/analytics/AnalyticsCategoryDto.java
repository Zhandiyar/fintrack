package kz.finance.fintrack.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsCategoryDto(
        String categoryName,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        List<ChartPointDto> chartData
) {
}

