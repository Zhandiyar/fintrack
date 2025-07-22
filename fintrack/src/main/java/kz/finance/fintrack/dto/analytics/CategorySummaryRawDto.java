package kz.finance.fintrack.dto.analytics;

import java.math.BigDecimal;

public record CategorySummaryRawDto(
        Long categoryId,
        String categoryNameRu,
        String categoryNameEn,
        String icon,
        String color,
        BigDecimal totalIncome,
        BigDecimal totalExpense
) {}
