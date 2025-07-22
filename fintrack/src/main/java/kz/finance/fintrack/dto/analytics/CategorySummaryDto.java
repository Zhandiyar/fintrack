package kz.finance.fintrack.dto.analytics;

import java.math.BigDecimal;

public record CategorySummaryDto(
        Long categoryId,
        String categoryName,
        String icon,
        String color,
        BigDecimal amount,
        BigDecimal percent
) {}
