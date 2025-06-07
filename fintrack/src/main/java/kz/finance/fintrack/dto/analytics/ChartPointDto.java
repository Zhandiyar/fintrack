package kz.finance.fintrack.dto.analytics;


import java.math.BigDecimal;

public record ChartPointDto(
        String label,
        BigDecimal income,
        BigDecimal expense
) {}
