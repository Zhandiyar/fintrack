package kz.finance.fintrack.dto.analytics;

import java.math.BigDecimal;

public record CategorySummaryDto(
        Long categoryId,
        String categoryNameRu,
        String categoryNameEn,
        BigDecimal totalIncome,
        BigDecimal totalExpense
) {}
