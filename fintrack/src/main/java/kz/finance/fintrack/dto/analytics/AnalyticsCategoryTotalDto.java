package kz.finance.fintrack.dto.analytics;

import java.math.BigDecimal;

public record AnalyticsCategoryTotalDto(
        BigDecimal income,
        BigDecimal expense
) {}

