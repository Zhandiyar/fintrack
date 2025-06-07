package kz.finance.fintrack.dto.analytics;

import java.math.BigDecimal;

public record AnalyticsStatsDto(
        BigDecimal currentIncome,
        BigDecimal currentExpense,
        BigDecimal previousIncome,
        BigDecimal previousExpense
) {}