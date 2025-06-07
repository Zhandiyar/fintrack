package kz.finance.fintrack.dto.analytics;

import java.math.BigDecimal;

public record DashboardStatsDto(
    // Общая статистика
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    
    // Статистика за текущий период
    BigDecimal currentPeriodIncome,
    BigDecimal currentPeriodExpense,
    
    // Статистика за предыдущий период
    BigDecimal previousPeriodIncome,
    BigDecimal previousPeriodExpense
) {} 