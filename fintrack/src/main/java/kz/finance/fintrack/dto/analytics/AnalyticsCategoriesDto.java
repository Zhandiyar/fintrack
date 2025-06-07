package kz.finance.fintrack.dto.analytics;

import java.util.List;

public record AnalyticsCategoriesDto(
        List<CategorySummaryDto> income,
        List<CategorySummaryDto> expense
) {
}
