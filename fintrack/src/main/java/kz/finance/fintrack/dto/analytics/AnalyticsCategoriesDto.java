package kz.finance.fintrack.dto.analytics;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsCategoriesDto(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        List<CategorySummaryDto> income,
        List<CategorySummaryDto> expense
) {
}
